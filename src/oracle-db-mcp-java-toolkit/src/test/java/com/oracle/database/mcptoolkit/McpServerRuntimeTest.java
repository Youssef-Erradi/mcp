/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2026 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.oracle.database.mcptoolkit.config.RuntimeConfigRoot;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerRuntimeTest {
  @Test
  void updatesTheActiveConfiguration() {
    LoadedConstants.initialize(RuntimeConfigRoot.fromProperties(Map.of("tools", "log-analyzer")));
    ServerConfig updatedConfig = ServerConfig.fromSystemProperties();

    McpServerRuntime.updateConfiguration(updatedConfig);

    assertSame(updatedConfig, McpServerRuntime.getConfig());
  }
}
