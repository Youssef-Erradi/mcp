/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2026 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.oracle.database.mcptoolkit.config.RuntimeConfigRoot;
import java.io.StringReader;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class LoadedConstantsTest {
  @AfterEach
  void resetConstants() {
    for (String property : new String[] {
        "transport", "https.port", "db.user", "db.password", "http.allowedOriginalHosts",
        "http.allowUnauthenticatedForDevelopment", "auth.enabled", "editTools.requireScope", "deepsec.enabled",
        "deepsec.databaseToken.tokenEndpoint", "oracle.ucp.createConnectionInBorrowThread"
    }) {
      System.clearProperty(property);
    }
    LoadedConstants.initialize(null);
  }

  @Test
  void loadsRuntimeSettingsFromYamlProperties() {
    RuntimeConfigRoot root = new RuntimeConfigRoot();
    root.systemProperties = new HashMap<>();
    root.systemProperties.put("transport", "HTTP");
    root.systemProperties.put("db.user", "yaml-user");
    root.systemProperties.put("db.password", "yaml-password");
    root.systemProperties.put("editTools.requireScope", "false");

    LoadedConstants.initialize(root);

    assertEquals("http", LoadedConstants.TRANSPORT_KIND);
    assertEquals("yaml-user", LoadedConstants.DB_USER);
    assertArrayEquals("yaml-password".toCharArray(), LoadedConstants.DB_PASSWORD);
    assertEquals(false, LoadedConstants.EDIT_TOOLS_REQUIRE_SCOPE);
  }

  @Test
  void systemPropertiesOverrideYamlProperties() {
    RuntimeConfigRoot root = new RuntimeConfigRoot();
    root.systemProperties = new HashMap<>();
    root.systemProperties.put("transport", "stdio");
    root.systemProperties.put("db.user", "yaml-user");
    System.setProperty("transport", "http");
    System.setProperty("db.user", "system-user");

    LoadedConstants.initialize(root);

    assertEquals("http", LoadedConstants.TRANSPORT_KIND);
    assertEquals("system-user", LoadedConstants.DB_USER);
  }

  @Test
  void parsesSystemPropertiesFromYaml() {
    RuntimeConfigRoot root = new Yaml().loadAs(new StringReader("""
        systemProperties:
          transport: http
          https.port: \"45451\"
          db.user: ${DB_USER}
        """), RuntimeConfigRoot.class);

    root.systemProperties.put("db.user", "yaml-user");
    LoadedConstants.initialize(root);

    assertEquals("http", LoadedConstants.TRANSPORT_KIND);
    assertEquals("45451", LoadedConstants.HTTPS_PORT);
    assertEquals("yaml-user", LoadedConstants.DB_USER);
  }

  @Test
  void loadsHttpSecurityAndDeepSecSettingsFromYamlProperties() {
    RuntimeConfigRoot root = new RuntimeConfigRoot();
    root.systemProperties = new HashMap<>();
    root.systemProperties.put("http.allowedOriginalHosts", "mcp.example.com");
    root.systemProperties.put("http.allowUnauthenticatedForDevelopment", "true");
    root.systemProperties.put("auth.enabled", "true");
    root.systemProperties.put("deepsec.enabled", "true");
    root.systemProperties.put("deepsec.databaseToken.tokenEndpoint", "https://identity.example.com/token");

    LoadedConstants.initialize(root);

    assertEquals("mcp.example.com", LoadedConstants.HTTP_ALLOWED_ORIGINAL_HOSTS);
    assertEquals(true, LoadedConstants.HTTP_ALLOW_UNAUTHENTICATED_FOR_DEVELOPMENT);
    assertEquals(true, LoadedConstants.AUTH_ENABLED);
    assertEquals(true, LoadedConstants.DEEPSEC_ENABLED);
    assertEquals("https://identity.example.com/token", LoadedConstants.DEEPSEC_DATABASE_TOKEN_ENDPOINT);
  }

  @Test
  void exposesYamlPropertiesToJdbcAndUcp() {
    RuntimeConfigRoot root = new RuntimeConfigRoot();
    root.systemProperties = new HashMap<>();
    root.systemProperties.put("oracle.ucp.createConnectionInBorrowThread", "true");

    LoadedConstants.initialize(root);

    assertEquals("true", System.getProperty("oracle.ucp.createConnectionInBorrowThread"));
  }
}
