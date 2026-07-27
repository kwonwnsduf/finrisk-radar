package com.finrisk.radar.payment;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

record PaymentRequestMetadata(
    UUID requestId, String ipHash, String userAgent, boolean clientRequestIdPresent) {

  static PaymentRequestMetadata from(HttpServletRequest request) {
    String provided = request.getHeader("X-Request-Id");
    UUID requestId;
    boolean present = provided != null && !provided.isBlank();
    try {
      requestId = present ? UUID.fromString(provided) : UUID.randomUUID();
    } catch (IllegalArgumentException ignored) {
      requestId = UUID.randomUUID();
      present = false;
    }
    return new PaymentRequestMetadata(
        requestId,
        hash(request.getRemoteAddr()),
        trim(request.getHeader("User-Agent"), 500),
        present);
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static String trim(String value, int size) {
    return value == null ? null : value.substring(0, Math.min(value.length(), size));
  }
}
