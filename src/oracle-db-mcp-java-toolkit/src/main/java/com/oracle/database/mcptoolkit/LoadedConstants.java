/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2025 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit;

import com.oracle.database.mcptoolkit.config.RuntimeConfigRoot;
import java.util.Map;

/** Provides runtime settings loaded from system properties and an optional YAML file. */
public final class LoadedConstants {
  private LoadedConstants() {}

  /** Identifies the existing YAML file for datasources and custom tools. */
  public static final String CONFIG_FILE = System.getProperty("configFile");
  /** Identifies the optional YAML file containing runtime settings. */
  public static final String RUNTIME_CONFIG_FILE = System.getProperty("runtimeConfigFile");

  /** Network config. */
  public static String TRANSPORT_KIND;
  public static String HTTPS_PORT;
  public static String KEYSTORE_PATH;
  public static String KEYSTORE_PASSWORD;
  public static String HTTP_ALLOWED_ORIGINAL_HOSTS;
  public static boolean HTTP_ALLOW_UNAUTHENTICATED_FOR_DEVELOPMENT;

  /** Tools and database config. */
  public static String TOOLS;
  public static String INGEST_ROOT_DIR;
  public static String INGEST_MAX_FILE_SIZE_MB;
  public static String DB_URL;
  public static String DB_USER;
  public static char[] DB_PASSWORD;
  public static int DB_TRANSACTION_IDLE_TIMEOUT_SECONDS;
  public static int DB_TRANSACTION_MAX_LIFETIME_SECONDS;
  public static int DB_MAX_TRANSACTIONS_PER_USER;

  /** OAuth config. */
  public static String ALLOWED_HOSTS;
  public static String AUTH_OPENID_DISCOVERY_REDIRECT_ENABLED;
  public static boolean AUTH_ENABLED;
  public static final String ORACLE_DB_TOOLKIT_AUTH_TOKEN = System.getenv("ORACLE_DB_TOOLKIT_AUTH_TOKEN");
  public static String AUTH_AUTHORIZATION_SERVER;
  public static String USER_TOKEN_INTROSPECTION_ENDPOINT;
  public static String USER_TOKEN_INTROSPECTION_CLIENT_ID;
  public static String USER_TOKEN_INTROSPECTION_CLIENT_SECRET;
  public static String OAUTH_SCOPE_CLAIM_PATH;
  public static boolean EDIT_TOOLS_REQUIRE_SCOPE;
  public static boolean LIST_CREDENTIALS_REQUIRE_SCOPE;
  public static String USER_TOKEN_VALIDATION_MODE;
  public static String USER_TOKEN_JWT_ISSUER;
  public static String USER_TOKEN_JWT_JWKS_URI;
  public static String USER_TOKEN_JWT_AUDIENCE;
  public static long USER_TOKEN_JWT_JWKS_CACHE_SECONDS;

  /** MCP OAuth discovery config. */
  public static String MCP_OAUTH_SCOPES;
  public static String MCP_OAUTH_RESOURCE_URL;

  /** Deep Data Security config. */
  public static boolean DEEPSEC_ENABLED;
  public static String DEEPSEC_DATABASE_TOKEN_STATIC_VALUE;
  public static String DEEPSEC_DATABASE_TOKEN_ENDPOINT;
  public static String DEEPSEC_DATABASE_TOKEN_CLIENT_ID;
  public static String DEEPSEC_DATABASE_TOKEN_CLIENT_SECRET;
  public static String DEEPSEC_DATABASE_TOKEN_SCOPE;

  /** External extensions. */
  public static String OJDBC_EXT_DIR;

  static {
    initialize(null);
  }

  /**
   * Applies YAML runtime settings. A non-blank JVM system property always takes precedence.
   *
   * @param configRoot parsed runtime YAML root, or {@code null} when no YAML file was supplied
   */
  public static void initialize(RuntimeConfigRoot configRoot) {
    Map<String, String> yamlProperties = configRoot == null ? null : configRoot.properties();
    applyYamlSystemProperties(yamlProperties);
    TRANSPORT_KIND = value("transport", yamlProperties, "stdio").trim().toLowerCase();
    HTTPS_PORT = value("https.port", yamlProperties, null);
    KEYSTORE_PATH = value("certificatePath", yamlProperties, null);
    KEYSTORE_PASSWORD = value("certificatePassword", yamlProperties, null);
    HTTP_ALLOWED_ORIGINAL_HOSTS = value("http.allowedOriginalHosts", yamlProperties, null);
    HTTP_ALLOW_UNAUTHENTICATED_FOR_DEVELOPMENT = bool(
        "http.allowUnauthenticatedForDevelopment", yamlProperties, false);
    TOOLS = value("tools", yamlProperties, null);
    INGEST_ROOT_DIR = value("ingestRootDir", yamlProperties, null);
    INGEST_MAX_FILE_SIZE_MB = value("ingestMaxFileSizeMb", yamlProperties, null);
    DB_URL = value("db.url", yamlProperties, null);
    DB_USER = value("db.user", yamlProperties, null);
    String dbPassword = value("db.password", yamlProperties, null);
    DB_PASSWORD = dbPassword == null ? null : dbPassword.toCharArray();
    DB_TRANSACTION_IDLE_TIMEOUT_SECONDS = integer("db.transactionIdleTimeoutSeconds", yamlProperties, 120);
    DB_TRANSACTION_MAX_LIFETIME_SECONDS = integer("db.transactionMaxLifetimeSeconds", yamlProperties, 300);
    DB_MAX_TRANSACTIONS_PER_USER = integer("db.maxTransactionsPerUser", yamlProperties, 4);
    ALLOWED_HOSTS = value("allowedHosts", yamlProperties, "*");
    AUTH_OPENID_DISCOVERY_REDIRECT_ENABLED = value("auth.openIdDiscoveryRedirectEnabled", yamlProperties, "false");
    AUTH_ENABLED = bool("auth.enabled", yamlProperties, false);
    AUTH_AUTHORIZATION_SERVER = value("auth.authorizationServer", yamlProperties, null);
    USER_TOKEN_INTROSPECTION_ENDPOINT = value("auth.userTokenValidation.introspection.endpoint", yamlProperties, null);
    USER_TOKEN_INTROSPECTION_CLIENT_ID = value("auth.userTokenValidation.introspection.clientId", yamlProperties, null);
    USER_TOKEN_INTROSPECTION_CLIENT_SECRET = value("auth.userTokenValidation.introspection.clientSecret", yamlProperties, null);
    OAUTH_SCOPE_CLAIM_PATH = value("oauth.scopeClaimPath", yamlProperties, "scope");
    EDIT_TOOLS_REQUIRE_SCOPE = bool("editTools.requireScope", yamlProperties, true);
    LIST_CREDENTIALS_REQUIRE_SCOPE = bool("listCredentials.requireScope", yamlProperties, true);
    USER_TOKEN_VALIDATION_MODE = value("auth.userTokenValidation.mode", yamlProperties, "introspection").trim().toLowerCase();
    USER_TOKEN_JWT_ISSUER = value("auth.userTokenValidation.jwt.issuer", yamlProperties, null);
    USER_TOKEN_JWT_JWKS_URI = value("auth.userTokenValidation.jwt.jwksUri", yamlProperties, null);
    USER_TOKEN_JWT_AUDIENCE = value("auth.userTokenValidation.jwt.audience", yamlProperties, null);
    USER_TOKEN_JWT_JWKS_CACHE_SECONDS = longValue("auth.userTokenValidation.jwt.jwksCacheSeconds", yamlProperties, 600);
    MCP_OAUTH_SCOPES = value("mcp.oauth.scopes", yamlProperties, "openid");
    MCP_OAUTH_RESOURCE_URL = value("mcp.oauth.resourceUrl", yamlProperties, null);
    DEEPSEC_ENABLED = bool("deepsec.enabled", yamlProperties, false);
    DEEPSEC_DATABASE_TOKEN_STATIC_VALUE = value("deepsec.databaseToken.staticValue", yamlProperties, null);
    DEEPSEC_DATABASE_TOKEN_ENDPOINT = value("deepsec.databaseToken.tokenEndpoint", yamlProperties, null);
    DEEPSEC_DATABASE_TOKEN_CLIENT_ID = value("deepsec.databaseToken.clientId", yamlProperties, null);
    DEEPSEC_DATABASE_TOKEN_CLIENT_SECRET = value("deepsec.databaseToken.clientSecret", yamlProperties, null);
    DEEPSEC_DATABASE_TOKEN_SCOPE = value("deepsec.databaseToken.scope", yamlProperties, null);
    OJDBC_EXT_DIR = value("ojdbc.ext.dir", yamlProperties, null);
  }

  /**
   * Makes runtime-YAML properties available to components such as JDBC and UCP that read JVM
   * properties directly. Existing non-blank JVM properties retain precedence.
   */
  private static void applyYamlSystemProperties(Map<String, String> yamlProperties) {
    if (yamlProperties == null) return;
    yamlProperties.forEach((name, yamlValue) -> {
      if (name == null || name.isBlank() || yamlValue == null || yamlValue.isBlank()) return;
      String systemValue = System.getProperty(name);
      if (systemValue == null || systemValue.isBlank()) {
        System.setProperty(name, yamlValue);
      }
    });
  }

  private static String value(String name, Map<String, String> yamlProperties, String defaultValue) {
    String systemValue = System.getProperty(name);
    if (systemValue != null && !systemValue.isBlank()) return systemValue;
    if (yamlProperties != null) {
      String yamlValue = yamlProperties.get(name);
      if (yamlValue != null && !yamlValue.isBlank()) return yamlValue;
    }
    return defaultValue;
  }

  private static boolean bool(String name, Map<String, String> yamlProperties, boolean defaultValue) {
    return Boolean.parseBoolean(value(name, yamlProperties, Boolean.toString(defaultValue)));
  }

  private static int integer(String name, Map<String, String> yamlProperties, int defaultValue) {
    return Integer.parseInt(value(name, yamlProperties, Integer.toString(defaultValue)));
  }

  private static long longValue(String name, Map<String, String> yamlProperties, long defaultValue) {
    return Long.parseLong(value(name, yamlProperties, Long.toString(defaultValue)));
  }
}
