/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2026 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit.spring;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Spring Boot configuration for embedding the toolkit's Streamable HTTP endpoint. */
@ConfigurationProperties("oracle.mcp")
public class OracleMcpProperties {
  private boolean enabled;
  private Map<String, Object> settings = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Toolkit runtime settings, using the same names as the standalone launcher. Nested YAML maps
   * are flattened into dotted property names before the toolkit is initialized.
   */
  public Map<String, Object> getSettings() {
    return settings;
  }

  public void setSettings(Map<String, Object> settings) {
    this.settings = settings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(settings);
  }
}
