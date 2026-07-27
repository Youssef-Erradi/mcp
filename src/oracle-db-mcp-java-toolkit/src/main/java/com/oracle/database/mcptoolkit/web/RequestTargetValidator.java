/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2025 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit.web;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates browser-facing request targets before MCP request handling. */
final class RequestTargetValidator {
  private final Set<String> allowedHosts;

  RequestTargetValidator(String configuredHosts) {
    allowedHosts = Arrays.stream(configuredHosts.split(","))
            .map(RequestTargetValidator::normalizeHost)
            .filter(host -> host != null)
            .collect(Collectors.toUnmodifiableSet());
    if (allowedHosts.isEmpty()) {
      throw new IllegalArgumentException("http.allowedHosts must contain at least one host");
    }
  }

  boolean allows(String requestHost, String origin) {
    if (!allowedHosts.contains(normalizeHost(requestHost))) {
      return false;
    }
    if (origin == null || origin.isBlank()) {
      return true;
    }
    try {
      URI originUri = URI.create(origin);
      return ("http".equalsIgnoreCase(originUri.getScheme()) || "https".equalsIgnoreCase(originUri.getScheme()))
              && allowedHosts.contains(normalizeHost(originUri.getHost()));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static String normalizeHost(String host) {
    if (host == null || host.isBlank()) {
      return null;
    }
    String normalized = host.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("[") && normalized.endsWith("]")) {
      normalized = normalized.substring(1, normalized.length() - 1);
    }
    while (normalized.endsWith(".")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized.isEmpty() ? null : normalized;
  }
}
