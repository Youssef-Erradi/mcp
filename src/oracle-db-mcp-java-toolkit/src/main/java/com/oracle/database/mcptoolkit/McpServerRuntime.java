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

  /** Reloads custom-tool configuration for an already running server. */
  public static synchronized void reloadConfiguration() {
    if (server == null) return;
    config = Utils.loadConfig();
    Utils.reloadCustomTools(server, config);
  }

  public static ServerConfig getConfig() {
    return config;
  }

  public static McpSyncServer getServer() {
    return server;
  }

  /** Registers a server created by the standalone launcher for shared admin-tool access. */
  public static synchronized void register(ServerConfig currentConfig, McpSyncServer currentServer) {
    config = currentConfig;
    server = currentServer;
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
