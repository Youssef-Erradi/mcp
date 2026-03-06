/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2025 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.oracle.database.mcptoolkit.ServerConfig;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
  private static final OAuth2TokenValidator INSTANCE = new OAuth2TokenValidator();
  private static final Logger LOGGER = Logger.getLogger(OAuth2TokenValidator.class.getName());

  private JWKSource<SecurityContext> jwkSource;
  private boolean isCacheDisabled;

  public static OAuth2TokenValidator getInstance(){
    return INSTANCE;
  }

  /**
   * Private constructor to initialize the OAuth2TokenValidator instance.
   * It retrieves the OAuth2 authorization server URL and client ID from the OAuth2Configuration singleton.
   * Then, it fetches the JSON Web Key Set (JWKS) URI from the OpenID configuration endpoint and creates a JWKSource instance.
   * The JWKSource is used for token validation.
   *
   * @throws RuntimeException if the JWKS URI is malformed
   */
  private OAuth2TokenValidator() {
    if (!OAUTH_CONFIG.isOAuth2Configured())
      return;

    final var oauthServerURL = OAUTH_CONFIG.getAuthServer();
    final var openIdConfigURISuffix = "/.well-known/openid-configuration";
    final var openIdConfigurationPath = oauthServerURL + openIdConfigURISuffix;
    final var jwksUrl = getJWKSURI(openIdConfigurationPath);

    if (isCacheDisabled)
      return;

    try {
      jwkSource = JWKSourceBuilder.create(URI.create(jwksUrl).toURL())
        .cache(true)
        .build();
    } catch (MalformedURLException e) {
      LOGGER.log(Level.SEVERE, e.getMessage(), e);
      throw new RuntimeException(e);
    }

  }

  /**
   * <p>Validates an OAuth2 access token (JWT).</p>
   *
   * <p>If OAuth2 is not configured, this method uses a local TokenGenerator to verify the token.</p>
   *
   * <p>If OAuth2 is configured, it performs token validation by:
   * <ol>
   *   <li>Parsing the token as a Signed JWT.</li>
   *   <li>Retrieving the JSON Web Key Set (JWKS) from the OAuth2 authorization server's OpenID configuration.</li>
   *   <li>Verifying the token's signature using the public RSA key from the JWKS.</li>
   *   <li>Checking the token's issuer, expiration time, not-before time claims. alongside Authorized Party if present.</li>
   * </ol>
   *</p>
   *
   * @param accessToken the OAuth2 access token to be validated
   * @return true if the token is valid, false otherwise
   */
  public boolean isTokenValid(final String accessToken, final String toolName) {
    if (accessToken == null || accessToken.isBlank())
      return false;

    if (!OAUTH_CONFIG.isOAuth2Configured())
      return TokenGenerator.getInstance()
        .verifyToken(accessToken);

    try {
      final SignedJWT signedJWT = SignedJWT.parse(accessToken);
      final JWKSelector selector = new JWKSelector(JWKMatcher.forJWSHeader(signedJWT.getHeader()));

      if (isCacheDisabled) {
        final var jwksUrl = getJWKSURI(OAUTH_CONFIG.getOpenIDConfigurationURI());
        jwkSource = JWKSourceBuilder.create(URI.create(jwksUrl).toURL())
          .cache(true)
          .build();
      }

      final List<JWK> jwks = jwkSource.get(selector, null);
      if (jwks == null || jwks.isEmpty())
        return false;

      // Verify signature using the first public RSA key
      final JWSVerifier verifier = new RSASSAVerifier(jwks.get(0).toRSAKey());
      if (!signedJWT.verify(verifier))
        return false;

      final JWTClaimsSet jwtClaims = signedJWT.getJWTClaimsSet();

      final Date now = new Date();

      final boolean validIssuer = OAUTH_CONFIG.getAuthServer().equals(jwtClaims.getIssuer());
      final boolean notExpired = jwtClaims.getExpirationTime().after(now);
      final boolean notBeforeTime = jwtClaims.getNotBeforeTime() == null || !jwtClaims.getNotBeforeTime().after(now);

      // 'azp' Authorized Party claim is not always present in tokens issued by OAuth2 servers.
      final boolean validAuthorizedParty = jwtClaims.getClaim("azp") == null ||
        OAUTH_CONFIG.getClientId() == null ||
        OAUTH_CONFIG.getClientId().equals(jwtClaims.getClaimAsString("azp"));

      System.out.println(jwtClaims.getClaims());

      final boolean hasValidScope = toolName.isBlank()  || jwtClaims.getClaim("scope") == null ||
        Arrays.stream(jwtClaims.getClaimAsString("scope").split(" "))
          .anyMatch(scope ->
            scope.contains(toolName) || scope.contains(ServerConfig.getToolSetByToolName(toolName).orElseThrow()));

      return validIssuer && notExpired && notBeforeTime && validAuthorizedParty && hasValidScope;

    } catch (ParseException | JOSEException | MalformedURLException e) {
      LOGGER.log(Level.SEVERE, e.getMessage(), e);
    }

    return false;
  }

  /**
   * <p>
   *   Retrieves the JSON Web Key Set (JWKS) URI from the OpenID configuration endpoint.
   * </p>
   *
   * @param openIdConfigurationURI the URI of the OpenID configuration endpoint
   * @return the JWKS URI if successfully retrieved, null otherwise
   */

  private String getJWKSURI(final String openIdConfigurationURI) {
    String jwksUri = null;

    try {
      final HttpClient client = HttpClient.newHttpClient();
      final HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(openIdConfigurationURI))
        .GET()
        .build();

      final HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      isCacheDisabled = response.headers()
        .firstValue("Cache-Control")
        .orElse("")
        .contains("no-cache");

      // TODO: max-age in case the OAuth2 server instructs the Client to cache the response

      final int statusCode = response.statusCode();
      if (statusCode == HttpServletResponse.SC_OK) {
        final var mapper = new ObjectMapper();
        final var jsonNode = mapper.readTree(response.body());

        jwksUri = jsonNode.get("jwks_uri").asText();
      }
    } catch (IOException | InterruptedException e) {
      LOGGER.log(Level.SEVERE, e.getMessage(), e);

      if (e instanceof InterruptedException)
        Thread.currentThread()
          .interrupt();
    }

    return jwksUri;
  }

}
