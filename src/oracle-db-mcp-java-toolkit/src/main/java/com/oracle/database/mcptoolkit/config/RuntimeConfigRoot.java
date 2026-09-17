/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2026 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit.config;

import com.oracle.database.mcptoolkit.EnvSubstitutor;
import java.util.Map;

/**
 * Represents runtime settings formerly supplied as JVM system properties.
 * This intentionally uses a separate YAML file from custom tool and datasource configuration.
 */
public class RuntimeConfigRoot {
  public Map<String, String> systemProperties;

  /** Substitutes environment variables in runtime settings. */
  public void substituteEnvVars() {
    if (systemProperties != null) {
      systemProperties.replaceAll((key, value) -> EnvSubstitutor.substituteEnvVars(value));
    }
  }
}
