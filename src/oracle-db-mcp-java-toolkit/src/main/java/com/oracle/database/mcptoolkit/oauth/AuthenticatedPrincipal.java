/*
 ** Copyright (c) 2026 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0.
 */

package com.oracle.database.mcptoolkit.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Stable request identity derived from an already validated OAuth access token. */
public record AuthenticatedPrincipal(
        String ownerId,
        String issuer,
        String subject,
        List<String> groups) {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static AuthenticatedPrincipal fromValidatedToken(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("Validated access token is required");
    }

    try {
      String[] parts = token.split("\\.");
      if (parts.length == 3) {
        JsonNode claims = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
        String issuer = text(claims, "iss");
        String subject = firstText(claims, "sub", "user_id", "username");
        if (subject != null) {
          return new AuthenticatedPrincipal(
                  fingerprint((issuer == null ? "" : issuer) + "\0" + subject),
                  issuer,
                  subject,
                  groups(claims));
        }
      }
    } catch (Exception ignored) {
      // Opaque tokens and non-standard JWT payloads use a token-bound fallback identity.
    }

    return new AuthenticatedPrincipal(
            fingerprint("token\0" + token), null, null, List.of());
  }

  private static String firstText(JsonNode claims, String... names) {
    for (String name : names) {
      String value = text(claims, name);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static String text(JsonNode claims, String name) {
    JsonNode value = claims.get(name);
    return value == null || !value.isTextual() || value.asText().isBlank()
            ? null
            : value.asText();
  }

  private static List<String> groups(JsonNode claims) {
    JsonNode value = claims.has("group") ? claims.get("group") : claims.get("groups");
    if (value == null) {
      return List.of();
    }
    List<String> groups = new ArrayList<>();
    if (value.isArray()) {
      value.forEach(group -> {
        if (group.isTextual() && !group.asText().isBlank()) {
          groups.add(group.asText());
        }
      });
    } else if (value.isTextual() && !value.asText().isBlank()) {
      groups.add(value.asText());
    }
    return List.copyOf(groups);
  }

  private static String fingerprint(String value) {
    try {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
