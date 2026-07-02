/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2025 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.database.mcptoolkit.LoadedConstants;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The OAuth2TokenValidator class is responsible for validating OAuth2 access tokens.
 * It checks if the provided access token is valid by either using a local TokenGenerator
 * if OAuth2 is not configured, or by performing token introspection against an OAuth2
 * authorization server if OAuth2 is properly configured.
 * <p>
 * This class relies on the OAuth2Configuration singleton to retrieve necessary settings
 * such as the introspection endpoint, client credentials, and configuration flags.
 * </p>
 */
public class OAuth2TokenValidator {
  private static final OAuth2Configuration OAUTH_CONFIG = OAuth2Configuration.getInstance();
  private static final Logger LOG = Logger.getLogger(OAuth2TokenValidator.class.getName());
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static volatile JwksCache jwksCache;

  /**
   * Validates the given access token.
   * <p>
   * If OAuth2 is not configured (as determined by OAuth2Configuration), this method
   * delegates validation to the TokenGenerator instance for local verification.
   * Otherwise, it performs an HTTP POST request to the OAuth2 introspection endpoint
   * using the configured client credentials. The response is parsed as JSON, and the
   * "active" field is checked to determine token validity.
   * </p>
   *
   * @param accessToken the OAuth2 access token to validate; must not be null or blank
   * @return true if the token is valid, false otherwise
   * @throws RuntimeException if an error occurs during token validation (e.g., network issues),
   *         though exceptions are logged and handled internally by returning false
   */
  public boolean isTokenValid(final String accessToken) {
    if (!OAUTH_CONFIG.isOAuth2Configured())
      return TokenGenerator.getInstance().verifyToken(accessToken);

    boolean isTokenValid = false;
    if (accessToken == null || accessToken.isBlank())
      return false;

    if ("jwt".equals(LoadedConstants.AUTH_VALIDATION_MODE))
      return isJwtTokenValid(accessToken);

    if (!"introspection".equals(LoadedConstants.AUTH_VALIDATION_MODE)) {
      LOG.log(Level.WARNING, () -> "Unsupported auth.validationMode: " + LoadedConstants.AUTH_VALIDATION_MODE);
      return false;
    }

    return isTokenValidByIntrospection(accessToken);
  }

  private boolean isTokenValidByIntrospection(final String accessToken) {
    boolean isTokenValid = false;
    final var clientCredentials = "%s:%s".formatted(OAUTH_CONFIG.getClientId(), OAUTH_CONFIG.getClientSecret());
    final var encodedClientCredentials = Base64.getEncoder()
      .encodeToString(clientCredentials.getBytes());
    final var requestBody = "token=" + URLEncoder.encode(accessToken, UTF_8);

    try {
      final HttpClient client = HttpClient.newHttpClient();
      final HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(OAUTH_CONFIG.getIntrospectionEndpoint()))
        .header("Authorization", "Basic " + encodedClientCredentials)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build();

      final HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

      final int statusCode = response.statusCode();
      if (statusCode == HttpServletResponse.SC_OK) {
        final var jsonNode = MAPPER.readTree(response.body());

        isTokenValid = jsonNode.get("active").asBoolean();
        if (!isTokenValid) {
          LOG.log(Level.WARNING, () -> "OAuth2 token introspection returned inactive token: " + response.body());
        }
      } else {
        LOG.log(Level.WARNING, () -> "OAuth2 token introspection failed with HTTP "
                + statusCode + ": " + response.body());
      }
    } catch (IOException | InterruptedException e) {
      LOG.log(Level.SEVERE, e.getMessage(), e);

      if (e instanceof InterruptedException)
        Thread.currentThread()
          .interrupt();
    }

    return isTokenValid;
  }

  private boolean isJwtTokenValid(final String accessToken) {
    try {
      requireJwtConfig();

      String[] parts = accessToken.split("\\.");
      if (parts.length != 3) {
        LOG.log(Level.WARNING, "JWT validation failed: token does not have three parts");
        return false;
      }

      JsonNode header = readJwtPart(parts[0]);
      JsonNode claims = readJwtPart(parts[1]);

      String algorithm = textClaim(header, "alg");
      if (!"RS256".equals(algorithm)) {
        LOG.log(Level.WARNING, () -> "JWT validation failed: unsupported alg " + algorithm);
        return false;
      }

      String keyId = textClaim(header, "kid");
      RSAPublicKey publicKey = getSigningKey(keyId);
      if (publicKey == null) {
        LOG.log(Level.WARNING, () -> "JWT validation failed: no JWKS key found for kid " + keyId);
        return false;
      }

      if (!verifySignature(parts[0] + "." + parts[1], parts[2], publicKey)) {
        LOG.log(Level.WARNING, "JWT validation failed: invalid signature");
        return false;
      }

      if (!Objects.equals(LoadedConstants.AUTH_ISSUER, textClaim(claims, "iss"))) {
        LOG.log(Level.WARNING, "JWT validation failed: issuer mismatch");
        return false;
      }

      if (!hasAudience(claims.get("aud"), LoadedConstants.AUTH_AUDIENCE)) {
        LOG.log(Level.WARNING, "JWT validation failed: audience mismatch");
        return false;
      }

      long now = Instant.now().getEpochSecond();
      JsonNode exp = claims.get("exp");
      if (exp == null || !exp.canConvertToLong() || exp.asLong() <= now) {
        LOG.log(Level.WARNING, "JWT validation failed: token expired or missing exp");
        return false;
      }

      JsonNode nbf = claims.get("nbf");
      if (nbf != null && nbf.canConvertToLong() && nbf.asLong() > now) {
        LOG.log(Level.WARNING, "JWT validation failed: token not active yet");
        return false;
      }

      return true;
    } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
      LOG.log(Level.WARNING, "JWT validation failed: " + e.getMessage(), e);
      return false;
    }
  }

  private void requireJwtConfig() {
    if (isBlank(LoadedConstants.AUTH_ISSUER))
      throw new IllegalArgumentException("auth.issuer is required for JWT validation");
    if (isBlank(LoadedConstants.AUTH_JWKS_URI))
      throw new IllegalArgumentException("auth.jwksUri is required for JWT validation");
    if (isBlank(LoadedConstants.AUTH_AUDIENCE))
      throw new IllegalArgumentException("auth.audience is required for JWT validation");
  }

  private JsonNode readJwtPart(String encodedPart) throws IOException {
    return MAPPER.readTree(Base64.getUrlDecoder().decode(encodedPart));
  }

  private RSAPublicKey getSigningKey(String keyId) throws IOException, GeneralSecurityException {
    JsonNode keys = getJwks().get("keys");
    if (keys == null || !keys.isArray()) {
      throw new IOException("JWKS response does not contain keys array");
    }

    for (JsonNode key : keys) {
      if (!"RSA".equals(textClaim(key, "kty"))) {
        continue;
      }
      String jwkKeyId = textClaim(key, "kid");
      if (keyId != null && !keyId.equals(jwkKeyId)) {
        continue;
      }
      JsonNode modulus = key.get("n");
      JsonNode exponent = key.get("e");
      if (modulus == null || exponent == null) {
        continue;
      }
      return toRsaPublicKey(modulus.asText(), exponent.asText());
    }
    return null;
  }

  private JsonNode getJwks() throws IOException {
    JwksCache cache = jwksCache;
    long now = Instant.now().getEpochSecond();
    if (cache != null && cache.expiresAtEpochSecond() > now) {
      return cache.jwks();
    }

    synchronized (OAuth2TokenValidator.class) {
      cache = jwksCache;
      now = Instant.now().getEpochSecond();
      if (cache != null && cache.expiresAtEpochSecond() > now) {
        return cache.jwks();
      }

      try {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LoadedConstants.AUTH_JWKS_URI))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
        if (response.statusCode() != HttpServletResponse.SC_OK) {
          throw new IOException("JWKS request failed with HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode jwks = MAPPER.readTree(response.body());
        long cacheSeconds = Math.max(LoadedConstants.AUTH_JWKS_CACHE_SECONDS, 1);
        jwksCache = new JwksCache(jwks, Instant.now().getEpochSecond() + cacheSeconds);
        return jwks;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while fetching JWKS", e);
      }
    }
  }

  private RSAPublicKey toRsaPublicKey(String encodedModulus, String encodedExponent)
          throws GeneralSecurityException {
    BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(encodedModulus));
    BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(encodedExponent));
    PublicKey publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(new RSAPublicKeySpec(modulus, exponent));
    return (RSAPublicKey) publicKey;
  }

  private boolean verifySignature(String signingInput, String encodedSignature, RSAPublicKey publicKey)
          throws GeneralSecurityException {
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initVerify(publicKey);
    signature.update(signingInput.getBytes(UTF_8));
    return signature.verify(Base64.getUrlDecoder().decode(encodedSignature));
  }

  private boolean hasAudience(JsonNode audienceClaim, String expectedAudience) {
    if (audienceClaim == null) {
      return false;
    }
    if (audienceClaim.isTextual()) {
      return expectedAudience.equals(audienceClaim.asText());
    }
    if (audienceClaim.isArray()) {
      for (JsonNode audience : audienceClaim) {
        if (expectedAudience.equals(audience.asText())) {
          return true;
        }
      }
    }
    return false;
  }

  private String textClaim(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record JwksCache(JsonNode jwks, long expiresAtEpochSecond) {}
}
