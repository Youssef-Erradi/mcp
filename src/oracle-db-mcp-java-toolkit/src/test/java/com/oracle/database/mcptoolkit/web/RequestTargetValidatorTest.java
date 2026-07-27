package com.oracle.database.mcptoolkit.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestTargetValidatorTest {
  private final RequestTargetValidator validator =
          new RequestTargetValidator("localhost,127.0.0.1,[::1],mcp.example.com");

  @Test
  void acceptsAllowedHostWithoutBrowserOrigin() {
    assertTrue(validator.allows("localhost", null));
  }

  @Test
  void rejectsReboundHost() {
    assertFalse(validator.allows("attacker.example", "https://attacker.example"));
  }

  @Test
  void rejectsUntrustedBrowserOrigin() {
    assertFalse(validator.allows("mcp.example.com", "https://attacker.example"));
  }

  @Test
  void acceptsConfiguredPublicHostAndOrigin() {
    assertTrue(validator.allows("mcp.example.com", "https://mcp.example.com"));
  }
}
