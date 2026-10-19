/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2025 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit;

import io.modelcontextprotocol.json.McpJsonDefaults;
import com.oracle.database.mcptoolkit.oauth.OAuth2Configuration;
import com.oracle.database.mcptoolkit.web.AuthorizationFilter;
import com.oracle.database.mcptoolkit.web.RedirectOAuthToOpenIDServlet;
import com.oracle.database.mcptoolkit.web.WebUtils;
import com.oracle.database.mcptoolkit.web.WellKnownServlet;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServlet;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.logging.Logger;

import static com.oracle.database.mcptoolkit.Utils.installExternalExtensionsFromDir;

/**
 * The OracleDatabaseMCPToolkit class provides the main entry point for the MCP server.
 * It initializes the configuration, sets up the transport layer, and starts the MCP server.
 */
public class OracleDatabaseMCPToolkit {
  private static final Logger LOG = Logger.getLogger(OracleDatabaseMCPToolkit.class.getName());

  static ServerConfig config;
  private static volatile McpSyncServer serverInstance;
  private static volatile McpStatelessSyncServer statelessServerInstance;


  static {
    LoadedConstants.initialize(Utils.loadRuntimeConfig());
    config = Utils.loadConfig();
  }

  public static void main(String[] args) {
    installExternalExtensionsFromDir();

    switch (LoadedConstants.TRANSPORT_KIND) {
      case "http" -> {
        startHttpServer();
      }
      case "stdio" -> {
        serverInstance = McpServer
          .sync(new StdioServerTransportProvider(McpJsonDefaults.getMapper()))
          .serverInfo("oracle-db-mcp-toolkit", "1.0.0")
          .capabilities(McpSchema.ServerCapabilities.builder()
             .tools(true)
             .logging()
             .build())
          .tools(Utils.getSyncToolSpecifications(config))
          .immediateExecution(true)
          .build();
      }
      default -> throw new IllegalArgumentException(
              "Unsupported transport: " + LoadedConstants.TRANSPORT_KIND + " (expected 'stdio' or 'http')");
    }
    // Start config file poller only when a config file path is provided
    if (LoadedConstants.CONFIG_FILE != null && !LoadedConstants.CONFIG_FILE.isBlank()) {
      Thread pollingThread = new Thread(() -> pollConfigFile(), "config-file-poller");
      pollingThread.setDaemon(true);
      pollingThread.start();
    } else {
      LOG.info("[oracle-db-mcp-toolkit] Config file not provided; polling disabled");
    }
  }

  private OracleDatabaseMCPToolkit() {
    // Prevent instantiation
  }

  /** Starts the configured HTTP MCP transport on /mcp using Tomcat. */
  private static void startHttpServer() {
    try {
      validateHttpSecurityConfiguration();
      HttpServlet mcpServlet;
      if (LoadedConstants.HTTP_STATELESS) {
        HttpServletStatelessServerTransport transport = HttpServletStatelessServerTransport.builder()
            .jsonMapper(McpJsonDefaults.getMapper())
            .messageEndpoint("/mcp")
            .contextExtractor(request -> Utils.captureStatelessTransportContext())
            .build();

        statelessServerInstance = McpServer.sync(transport)
            .serverInfo("oracle-db-mcp-toolkit", "1.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
            .tools(Utils.getStatelessToolSpecifications(config))
            .immediateExecution(true)
            .build();
        mcpServlet = transport;
        LOG.info("[oracle-db-mcp-toolkit] Stateless HTTP mode enabled");
      } else {
        HttpServletStreamableServerTransportProvider transport =
          HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(McpJsonDefaults.getMapper())
            .keepAliveInterval(Duration.ofSeconds(60))
            .mcpEndpoint("/mcp")
            .build();

        serverInstance = McpServer
          .sync(transport)
          .serverInfo("oracle-db-mcp-toolkit", "1.0.0")
          .capabilities(McpSchema.ServerCapabilities.builder()
             .tools(true)
             .logging()
             .build())
          .tools(Utils.getSyncToolSpecifications(config))
          .immediateExecution(true)
          .build();
        mcpServlet = transport;
      }

      Tomcat tomcat = new Tomcat();
      String ctxPath = "";
      String docBase = new File(".").getAbsolutePath();
      Context ctx = tomcat.addContext(ctxPath, docBase);

      Tomcat.addServlet(ctx, "mcpServlet", mcpServlet);
      ctx.addServletMappingDecoded("/mcp/*", "mcpServlet");

      Tomcat.addServlet(ctx, "wellKnownServlet", new WellKnownServlet());
      ctx.addServletMappingDecoded(
              "/.well-known/oauth-protected-resource", "wellKnownServlet");

      if (OAuth2Configuration.getInstance().isAuthorizationServerConfigured()
              && WebUtils.isRedirectOpenIDToOAuthEnabled()) {
        Tomcat.addServlet(ctx, "redirectOAuthToOpenIDServlet", new RedirectOAuthToOpenIDServlet());
        ctx.addServletMappingDecoded("/.well-known/oauth-authorization-server", "redirectOAuthToOpenIDServlet");
      }

      FilterDef filterDef = new FilterDef();
      filterDef.setFilterName("authFilter");
      filterDef.setFilter(new AuthorizationFilter());
      filterDef.setFilterClass(AuthorizationFilter.class.getName());
      ctx.addFilterDef(filterDef);

      FilterMap filterMap = new FilterMap();
      filterMap.setFilterName("authFilter");
      filterMap.addURLPattern("/mcp/*");
      ctx.addFilterMap(filterMap);

      if (LoadedConstants.HTTPS_PORT == null || LoadedConstants.KEYSTORE_PATH == null || LoadedConstants.KEYSTORE_PASSWORD == null)
        throw new RuntimeException("SSL setup failed: HTTPS port, Keystore path or password not specified");

      enableHttps(tomcat);

      tomcat.start();

      LOG.info("[oracle-db-mcp-toolkit] HTTP transport started on " + LoadedConstants.HTTPS_PORT + " (endpoint: /mcp)");
    } catch (Exception e) {
      throw new RuntimeException("Failed to start HTTP MCP server", e);
    }
  }

  /**
   * Configures and enables HTTPS on the provided Tomcat server using the specified keystore.
   *
   * @param tomcat          the Tomcat server instance to configure
   * @throws RuntimeException if the HTTPS connector or SSL configuration fails
   */
  private static void enableHttps(Tomcat tomcat) {
    try {
      // Create HTTPS connector
      Connector https = new Connector("org.apache.coyote.http11.Http11NioProtocol");
      https.setPort(Integer.parseInt(LoadedConstants.HTTPS_PORT));
      https.setSecure(true);
      https.setScheme("https");
      https.setProperty("SSLEnabled", "true");

      // Create SSL config
      SSLHostConfig sslHostConfig = new SSLHostConfig();
      sslHostConfig.setHostName("_default_");

      SSLHostConfigCertificate cert = new SSLHostConfigCertificate(
          sslHostConfig,
          SSLHostConfigCertificate.Type.RSA
      );

      cert.setCertificateKeystoreFile(LoadedConstants.KEYSTORE_PATH);
      cert.setCertificateKeystorePassword(LoadedConstants.KEYSTORE_PASSWORD);
      cert.setCertificateKeystoreType("PKCS12");

      // Attach certificate to SSL config
      sslHostConfig.addCertificate(cert);

      // Enable SSL
      https.addSslHostConfig(sslHostConfig);

      // Register connector
      tomcat.getService().addConnector(https);

    } catch (Exception e) {
      throw new RuntimeException("Failed to enable HTTPS on Tomcat", e);
    }
  }

  private static void validateHttpSecurityConfiguration() {
    if (!OAuth2Configuration.getInstance().isAuthenticationEnabled()) {
      if (!LoadedConstants.HTTP_ALLOW_UNAUTHENTICATED_FOR_DEVELOPMENT) {
        throw new IllegalStateException(
                "HTTP transport requires authentication. Set -Dauth.enabled=true or, for local "
                        + "development only, -Dhttp.allowUnauthenticatedForDevelopment=true");
      }
      LOG.warning("[oracle-db-mcp-toolkit] Starting unauthenticated HTTP transport for development only");
    }

  }

  public static ServerConfig getConfig() {
    return config;
  }

  /** Removes a tool from whichever HTTP or stdio server is running. */
  public static void removeToolFromRunningServer(String name) {
    McpSyncServer syncServer = serverInstance;
    if (syncServer != null) syncServer.removeTool(name);
    McpStatelessSyncServer statelessServer = statelessServerInstance;
    if (statelessServer != null) statelessServer.removeTool(name);
  }

  private static void reloadConfigAndResetTools() {
    try {
      LOG.info(()->"[DEBUG] Reloading config...");
      ServerConfig newConfig = Utils.loadConfig();
      LOG.info(()->"[DEBUG] Old custom tool names: " + Utils.customToolNames);
      LOG.info(()->"[DEBUG] New config tool names: " + newConfig.tools.keySet());
      config = newConfig;      // update reference
      if (statelessServerInstance != null) {
        Utils.reloadCustomTools(statelessServerInstance, newConfig);
      } else if (serverInstance != null) {
        Utils.reloadCustomTools(serverInstance, newConfig);
      }
      LOG.info(()->"[DEBUG] Reloaded config and refreshed tools.");
    } catch (Exception e) {
      System.err.println("[oracle-db-mcp-toolkit] Failed to reload config: " + e);
    }
  }

  // For now, we rely on this instead of the nio watcher logic (for container’s sake)
  private static void pollConfigFile() {
    // Guard against null/blank to avoid NPE when no -DconfigFile is supplied
    if (LoadedConstants.CONFIG_FILE == null || LoadedConstants.CONFIG_FILE.isBlank()) {
      LOG.info("[oracle-db-mcp-toolkit] CONFIG_FILE not set; skipping config polling.");
      return;
    }
    File configFile = new File(LoadedConstants.CONFIG_FILE);
    long lastModified = configFile.lastModified();
    while (true) {
      long nowModified = configFile.lastModified();
      if (nowModified != lastModified && nowModified != 0) {
        lastModified = nowModified;
        reloadConfigAndResetTools();
      }
      try {
        Thread.sleep(2000); // Check every 2 seconds
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }
}
