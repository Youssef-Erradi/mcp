/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2026 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit;

import com.oracle.database.mcptoolkit.oauth.OAuth2Configuration;
import com.oracle.database.mcptoolkit.config.RuntimeConfigRoot;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Objects;
import java.util.logging.Logger;

/** Creates toolkit MCP servers without owning the embedding application's web-server lifecycle. */
public final class McpServerRuntime {
  private static final Logger LOG = Logger.getLogger(McpServerRuntime.class.getName());
  private static volatile ServerConfig config;
  private static volatile McpSyncServer server;

  private McpServerRuntime() {}

  /** Creates an HTTP MCP server using a transport registered by the embedding application. */
  public static synchronized McpSyncServer createHttpServer(
      HttpServletStreamableServerTransportProvider transport) {
    return createHttpServer(transport, Utils.loadRuntimeConfig());
  }

  /** Creates an HTTP MCP server using runtime settings supplied by an embedding application. */
  public static synchronized McpSyncServer createHttpServer(
      HttpServletStreamableServerTransportProvider transport, RuntimeConfigRoot runtimeConfig) {
    initializeConfiguration(runtimeConfig);
    validateHttpSecurityConfiguration();
    server = createServer(transport);
    return server;
  }

  /** Creates a standard-input/output MCP server for the standalone launcher. */
  public static synchronized McpSyncServer createStdioServer() {
    initializeConfiguration(Utils.loadRuntimeConfig());
    server = McpServer
      .sync(new StdioServerTransportProvider(McpJsonDefaults.getMapper()))
      .serverInfo("oracle-db-mcp-toolkit", "1.0.0")
      .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
      .tools(Utils.getSyncToolSpecifications(config))
      .immediateExecution(true)
      .build();
    return server;
  }

  /** Reloads custom-tool configuration from the configured YAML file. */
  public static synchronized void reloadCustomToolConfiguration() {
    updateConfiguration(Utils.loadConfig());
  }

  /**
   * Replaces the active toolkit configuration and refreshes custom tools on the running server.
   *
   * <p>Embedding applications that manage configuration themselves can use this instead of the
   * YAML-file reload path.</p>
   */
  public static synchronized void updateConfiguration(ServerConfig updatedConfig) {
    config = Objects.requireNonNull(updatedConfig, "updatedConfig");
    if (server != null) {
      Utils.reloadCustomTools(server, config);
    }
  }

  public static ServerConfig getConfig() {
    return config;
  }

  public static McpSyncServer getServer() {
    return server;
  }

  private static void initializeConfiguration(RuntimeConfigRoot runtimeConfig) {
    LoadedConstants.initialize(runtimeConfig);
    Utils.installExternalExtensionsFromDir();
    config = Utils.loadConfig();
  }

  private static McpSyncServer createServer(
      HttpServletStreamableServerTransportProvider transport) {
    return McpServer
      .sync(transport)
      .serverInfo("oracle-db-mcp-toolkit", "1.0.0")
      .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
      .tools(Utils.getSyncToolSpecifications(config))
      .immediateExecution(true)
      .build();
  }

  private static void validateHttpSecurityConfiguration() {
    if (!OAuth2Configuration.getInstance().isAuthenticationEnabled()
        && !LoadedConstants.HTTP_ALLOW_UNAUTHENTICATED_FOR_DEVELOPMENT) {
      throw new IllegalStateException(
        "HTTP transport requires authentication. Set auth.enabled=true or, for local development "
          + "only, set http.allowUnauthenticatedForDevelopment=true");
    }
    if (!OAuth2Configuration.getInstance().isAuthenticationEnabled()) {
      LOG.warning("[oracle-db-mcp-toolkit] Starting unauthenticated HTTP transport for development only");
    }
  }
}
