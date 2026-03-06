package com.oracle.database.mcptoolkit.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
  private final byte[] cachedBody;

  public CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
    super(request);
    cachedBody = request.getInputStream().readAllBytes();
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);

    return new ServletInputStream() {
      @Override public int read() { return bais.read(); }
      @Override public boolean isFinished() { return bais.available() == 0; }
      @Override public boolean isReady() { return true; }
      @Override public void setReadListener(ReadListener readListener) { /* no-op */ }
    };
  }

  @Override
  public BufferedReader getReader() {
    final var bais = new ByteArrayInputStream(cachedBody);
    final var charset = getCharacterEncoding() != null ? Charset.forName(getCharacterEncoding()) : StandardCharsets.UTF_8;
    return new BufferedReader(new InputStreamReader(bais, charset));
  }
}