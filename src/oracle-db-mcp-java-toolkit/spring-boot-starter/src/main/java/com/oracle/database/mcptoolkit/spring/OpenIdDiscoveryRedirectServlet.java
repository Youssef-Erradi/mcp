/*
 ** Oracle Database MCP Toolkit version 1.0.0
 **
 ** Copyright (c) 2026 Oracle and/or its affiliates.
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */

package com.oracle.database.mcptoolkit.spring;

import com.oracle.database.mcptoolkit.LoadedConstants;
import com.oracle.database.mcptoolkit.oauth.OAuth2Configuration;
import com.oracle.database.mcptoolkit.web.WebUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Exposes the optional OAuth authorization-server metadata redirect in a Spring application. */
final class OpenIdDiscoveryRedirectServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    OAuth2Configuration configuration = OAuth2Configuration.getInstance();
    if (!configuration.isAuthorizationServerConfigured()
        || !WebUtils.isRedirectOpenIDToOAuthEnabled()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    response.addHeader("Access-Control-Allow-Origin", LoadedConstants.CORS_ALLOWED_ORIGIN);
    response.sendRedirect(configuration.getAuthServer() + "/.well-known/openid-configuration");
  }
}
