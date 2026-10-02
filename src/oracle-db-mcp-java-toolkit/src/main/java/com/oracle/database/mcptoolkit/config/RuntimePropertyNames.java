/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2026 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit.config;

import java.util.Map;

/** Resolves public runtime property names to the legacy names used by existing consumers. */
public final class RuntimePropertyNames {
  private static final Map<String, String> LEGACY_NAMES = Map.ofEntries(
      Map.entry("network.transport", "transport"),
      Map.entry("network.http.port", "http.port"),
      Map.entry("network.http.allowedOriginalHosts", "http.allowedOriginalHosts"),
      Map.entry("network.http.allowUnauthenticatedForDevelopment", "http.allowUnauthenticatedForDevelopment"),
      Map.entry("network.https.port", "https.port"),
      Map.entry("network.https.certificatePath", "certificatePath"),
      Map.entry("network.https.certificatePassword", "certificatePassword"),
      Map.entry("toolSelection.enabled", "tools"),
      Map.entry("dataIngestion.rootDirectory", "ingestRootDir"),
      Map.entry("dataIngestion.maxFileSizeMb", "ingestMaxFileSizeMb"),
      Map.entry("database.url", "db.url"), Map.entry("database.user", "db.user"),
      Map.entry("database.password", "db.password"),
      Map.entry("database.transactions.idleTimeoutSeconds", "db.transactionIdleTimeoutSeconds"),
      Map.entry("database.transactions.maxLifetimeSeconds", "db.transactionMaxLifetimeSeconds"),
      Map.entry("database.transactions.maxPerUser", "db.maxTransactionsPerUser"),
      Map.entry("database.jdbc.extensionsDirectory", "ojdbc.ext.dir"),
      Map.entry("userAuth.enabled", "auth.enabled"), Map.entry("userAuth.allowedCorsHosts", "allowedHosts"),
      Map.entry("userAuth.authorizationServer", "auth.authorizationServer"),
      Map.entry("userAuth.openIdDiscoveryRedirectEnabled", "auth.openIdDiscoveryRedirectEnabled"),
      Map.entry("userAuth.tokenValidation.introspection.scopeClaimPath", "oauth.scopeClaimPath"),
      Map.entry("deepDataSecurity.enabled", "deepsec.enabled"),
      Map.entry("deepDataSecurity.databaseToken.staticValue", "deepsec.databaseToken.staticValue"),
      Map.entry("deepDataSecurity.databaseToken.tokenEndpoint", "deepsec.databaseToken.tokenEndpoint"),
      Map.entry("deepDataSecurity.databaseToken.clientId", "deepsec.databaseToken.clientId"),
      Map.entry("deepDataSecurity.databaseToken.clientSecret", "deepsec.databaseToken.clientSecret"),
      Map.entry("deepDataSecurity.databaseToken.scope", "deepsec.databaseToken.scope"));

  private RuntimePropertyNames() {}

  public static String legacyName(String name) {
    String mapped = LEGACY_NAMES.get(name);
    if (mapped != null) return mapped;
    if (name.startsWith("userAuth.tokenValidation.")) {
      return "auth.userTokenValidation." + name.substring("userAuth.tokenValidation.".length());
    }
    return name;
  }
}
