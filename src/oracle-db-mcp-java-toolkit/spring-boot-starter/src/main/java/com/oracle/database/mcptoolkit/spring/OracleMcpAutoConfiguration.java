/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2026 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit.spring;

import com.oracle.database.mcptoolkit.LoadedConstants;
import com.oracle.database.mcptoolkit.McpServerRuntime;
import com.oracle.database.mcptoolkit.Utils;
import com.oracle.database.mcptoolkit.config.RuntimeConfigRoot;
import com.oracle.database.mcptoolkit.web.AuthorizationFilter;
import com.oracle.database.mcptoolkit.web.WellKnownServlet;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

/** Registers the toolkit on a Spring Boot servlet application's existing web server. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "oracle.mcp", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OracleMcpProperties.class)
public class OracleMcpAutoConfiguration {
  private static final String MCP_PATH = "/mcp";

  @Bean
  ToolkitRuntimeSettings toolkitRuntimeSettings(OracleMcpProperties properties) {
    RuntimeConfigRoot runtimeConfig = RuntimeConfigRoot.fromYaml(properties.getSettings());
    runtimeConfig.substituteEnvVars();
    LoadedConstants.initialize(runtimeConfig);
    return new ToolkitRuntimeSettings(runtimeConfig);
  }

  @Bean
  @ConditionalOnSingleCandidate(DataSource.class)
  ToolkitDataSource toolkitDataSource(DataSource dataSource, ToolkitRuntimeSettings ignored) {
    Utils.setDefaultDataSource(dataSource);
    return new ToolkitDataSource();
  }

  @Bean
  @ConditionalOnMissingBean
  HttpServletStreamableServerTransportProvider oracleMcpTransport(
      ToolkitRuntimeSettings ignored) {
    return HttpServletStreamableServerTransportProvider.builder()
      .jsonMapper(McpJsonDefaults.getMapper())
      .keepAliveInterval(Duration.ofSeconds(60))
      .mcpEndpoint(MCP_PATH)
      .build();
  }

  @Bean(destroyMethod = "closeGracefully")
  @ConditionalOnMissingBean(name = "oracleMcpServer")
  McpSyncServer oracleMcpServer(
      HttpServletStreamableServerTransportProvider transport, ToolkitRuntimeSettings ignored,
      ObjectProvider<ToolkitDataSource> dataSource) {
    dataSource.getIfAvailable();
    return McpServerRuntime.createHttpServer(transport, ignored.runtimeConfig);
  }

  @Bean
  @ConditionalOnMissingBean(name = "oracleMcpServlet")
  ServletRegistrationBean<HttpServletStreamableServerTransportProvider> oracleMcpServlet(
      HttpServletStreamableServerTransportProvider transport, ToolkitRuntimeSettings ignored) {
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
      new ServletRegistrationBean<>(transport, MCP_PATH, MCP_PATH + "/*");
    registration.setName("oracleMcpServlet");
    registration.setAsyncSupported(true);
    return registration;
  }

  @Bean
  @ConditionalOnMissingBean(name = "oracleMcpAuthorizationFilter")
  FilterRegistrationBean<AuthorizationFilter> oracleMcpAuthorizationFilter(
      ToolkitRuntimeSettings ignored) {
    FilterRegistrationBean<AuthorizationFilter> registration =
      new FilterRegistrationBean<>(new AuthorizationFilter());
    registration.setName("oracleMcpAuthorizationFilter");
    registration.addUrlPatterns(MCP_PATH, MCP_PATH + "/*");
    registration.setAsyncSupported(true);
    return registration;
  }

  @Bean
  @ConditionalOnMissingBean(name = "oracleMcpProtectedResourceServlet")
  ServletRegistrationBean<WellKnownServlet> oracleMcpProtectedResourceServlet(
      ToolkitRuntimeSettings ignored) {
    return new ServletRegistrationBean<>(
      new WellKnownServlet(), "/.well-known/oauth-protected-resource");
  }

  @Bean
  @ConditionalOnMissingBean(name = "oracleMcpAuthorizationServerServlet")
  ServletRegistrationBean<OpenIdDiscoveryRedirectServlet> oracleMcpAuthorizationServerServlet(
      ToolkitRuntimeSettings ignored) {
    return new ServletRegistrationBean<>(
      new OpenIdDiscoveryRedirectServlet(), "/.well-known/oauth-authorization-server");
  }

  /** Ensures toolkit settings are initialized before servlet and server beans are constructed. */
  static final class ToolkitRuntimeSettings {
    private final RuntimeConfigRoot runtimeConfig;

    ToolkitRuntimeSettings(RuntimeConfigRoot runtimeConfig) {
      this.runtimeConfig = runtimeConfig;
    }
  }

  /** Marks that Spring has supplied the toolkit's default data source. */
  static final class ToolkitDataSource {}
}
