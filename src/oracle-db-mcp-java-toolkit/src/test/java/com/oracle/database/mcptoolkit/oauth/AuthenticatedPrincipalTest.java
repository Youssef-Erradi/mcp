/*
 ** Copyright (c) 2026 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0.
 */

package com.oracle.database.mcptoolkit.oauth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthenticatedPrincipalTest {
  @AfterEach
  void clearContext() {
    EndUserSecurityContextHolder.clear();
  }

  @Test
  void derivesStableOwnerFromIssuerAndSubjectAcrossTokens() {
    AuthenticatedPrincipal first = AuthenticatedPrincipal.fromValidatedToken(jwt(
            "{\"iss\":\"https://identity.example\",\"sub\":\"alice\","
                    + "\"group\":[\"CustomerReaders\"]}"));
    AuthenticatedPrincipal refreshed = AuthenticatedPrincipal.fromValidatedToken(jwt(
            "{\"iss\":\"https://identity.example\",\"sub\":\"alice\",\"exp\":9999999999}"));
    AuthenticatedPrincipal bob = AuthenticatedPrincipal.fromValidatedToken(jwt(
            "{\"iss\":\"https://identity.example\",\"sub\":\"bob\"}"));

    assertEquals(first.ownerId(), refreshed.ownerId());
    assertNotEquals(first.ownerId(), bob.ownerId());
    assertEquals("alice", first.subject());
    assertEquals("CustomerReaders", first.groups().get(0));
  }

  @Test
  void keepsOnlyPrincipalInRequestHolderWhenDeepSecIsDisabled() {
    AuthenticatedPrincipal principal = AuthenticatedPrincipal.fromValidatedToken("opaque-token");
    EndUserSecurityContextHolder.setPrincipal(principal);

    assertEquals(principal, EndUserSecurityContextHolder.getAuthenticatedPrincipal());
    assertNull(new EndUserSecurityContextHolder().getEndUserSecurityContext(null));
  }

  private String jwt(String payload) {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(UTF_8))
            + "." + encoder.encodeToString(payload.getBytes(UTF_8))
            + ".signature";
  }
}
