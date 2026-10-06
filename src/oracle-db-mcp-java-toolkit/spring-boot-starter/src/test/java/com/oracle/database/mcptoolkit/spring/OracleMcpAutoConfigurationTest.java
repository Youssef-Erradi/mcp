/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2026 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracle.database.mcptoolkit.LoadedConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class OracleMcpAutoConfigurationTest {
  private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(OracleMcpAutoConfiguration.class));

  @AfterEach
  void restoreLegacyRuntimeSettings() {
    LoadedConstants.initialize(null);
  }

  @Test
  void registersToolkitOnTheApplicationServletContainer() {
    contextRunner
      .withPropertyValues(
        "oracle.mcp.enabled=true",
        "oracle.mcp.settings.tools=log-analyzer",
        "oracle.mcp.settings.http.allowUnauthenticatedForDevelopment=true")
      .run(context -> {
        assertThat(context).hasSingleBean(OracleMcpProperties.class);
        assertThat(context).hasBean("oracleMcpServer");
        assertThat(context).hasBean("oracleMcpServlet");
        assertThat(LoadedConstants.TOOLS).isEqualTo("log-analyzer");
      });
  }
}
