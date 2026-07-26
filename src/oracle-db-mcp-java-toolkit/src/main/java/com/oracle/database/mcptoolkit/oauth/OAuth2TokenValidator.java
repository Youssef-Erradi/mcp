/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2025 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.DefaultJWKSetCache;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.oracle.database.mcptoolkit.LoadedConstants;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.ParseException;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The OAuth2TokenValidator class is responsible for validating OAuth2 access tokens.
 * It checks if the provided access token is valid by either using a local TokenGenerator
 * if OAuth2 is not configured, validating a JWT against the authorization server's JWKS,
 * or performing token introspection against an OAuth2 authorization server.
 * <p>
 * This class relies on the OAuth2Configuration singleton to retrieve necessary settings
 * such as the introspection endpoint, client credentials, and configuration flags.
 * </p>
 */
public class OAuth2TokenValidator {
  private static final OAuth2Configuration OAUTH_CONFIG = OAuth2Configuration.getInstance();
  private static final Logger LOG = Logger.getLogger(OAuth2TokenValidator.class.getName());
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static volatile JwtProcessorCache jwtProcessorCache;

  /**
   * Validates the given access token.
   * <p>
   * If OAuth2 is not configured (as determined by OAuth2Configuration), this method
   * delegates validation to the TokenGenerator instance for local verification.
   * Otherwise, the configured validation mode determines whether the token is verified
   * locally against the configured JWKS or introspected with the OAuth2 authorization server.
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
      jwtProcessor().process(accessToken, null);
      return true;
    } catch (IOException | ParseException | BadJOSEException | com.nimbusds.jose.JOSEException
             | IllegalArgumentException e) {
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

  private DefaultJWTProcessor<SecurityContext> jwtProcessor() throws IOException {
    JwtProcessorCache cache = jwtProcessorCache;
    if (cache != null && cache.matchesCurrentConfiguration()) {
      return cache.processor();
    }

    synchronized (OAuth2TokenValidator.class) {
      cache = jwtProcessorCache;
      if (cache != null && cache.matchesCurrentConfiguration()) {
        return cache.processor();
      }

      long cacheSeconds = Math.max(LoadedConstants.AUTH_JWKS_CACHE_SECONDS, 1);
      RemoteJWKSet<SecurityContext> jwkSource = new RemoteJWKSet<>(
              new URL(LoadedConstants.AUTH_JWKS_URI),
              null,
              new DefaultJWKSetCache(cacheSeconds, cacheSeconds, TimeUnit.SECONDS));
      DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
      processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));

      DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier = new DefaultJWTClaimsVerifier<>(
              Set.of(LoadedConstants.AUTH_AUDIENCE),
              new JWTClaimsSet.Builder().issuer(LoadedConstants.AUTH_ISSUER).build(),
              Set.of("iss", "aud", "exp"),
              Set.of());
      claimsVerifier.setMaxClockSkew(0);
      processor.setJWTClaimsSetVerifier(claimsVerifier);
      jwtProcessorCache = new JwtProcessorCache(
              processor,
              LoadedConstants.AUTH_ISSUER,
              LoadedConstants.AUTH_JWKS_URI,
              LoadedConstants.AUTH_AUDIENCE,
              cacheSeconds);
      return processor;
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record JwtProcessorCache(
          DefaultJWTProcessor<SecurityContext> processor,
          String issuer,
          String jwksUri,
          String audience,
          long cacheSeconds) {
    private boolean matchesCurrentConfiguration() {
      return issuer.equals(LoadedConstants.AUTH_ISSUER)
              && jwksUri.equals(LoadedConstants.AUTH_JWKS_URI)
              && audience.equals(LoadedConstants.AUTH_AUDIENCE)
              && cacheSeconds == Math.max(LoadedConstants.AUTH_JWKS_CACHE_SECONDS, 1);
    }
  }
}
