package com.finrisk.radar.global.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
  public static final String HEADER = "X-Request-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = validUuid(request.getHeader(HEADER));
    if (requestId == null) requestId = UUID.randomUUID().toString();
    response.setHeader(HEADER, requestId);
    try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
      chain.doFilter(request, response);
    }
  }

  private String validUuid(String value) {
    try {
      return value == null ? null : UUID.fromString(value).toString();
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
