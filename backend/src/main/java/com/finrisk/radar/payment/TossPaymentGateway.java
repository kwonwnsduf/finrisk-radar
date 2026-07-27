package com.finrisk.radar.payment;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.client.*;

final class TossPaymentGateway implements PaymentGateway {
  private final RestClient client;
  private final String authorization;

  TossPaymentGateway(RestClient client, String secretKey) {
    this.client = client;
    this.authorization =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public GatewayPayment confirmPayment(
      String paymentKey, String orderId, long amount, UUID idempotencyKey) {
    return post(
        "/v1/payments/confirm",
        Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount),
        idempotencyKey);
  }

  @Override
  public GatewayPayment cancelPayment(String paymentKey, String reason, UUID idempotencyKey) {
    return post(
        "/v1/payments/{paymentKey}/cancel",
        Map.of("cancelReason", reason),
        idempotencyKey,
        paymentKey);
  }

  @Override
  public GatewayPayment getPayment(String paymentKey) {
    return get("/v1/payments/{paymentKey}", paymentKey);
  }

  @Override
  public GatewayPayment getPaymentByOrderId(String orderId) {
    return get("/v1/payments/orders/{orderId}", orderId);
  }

  private GatewayPayment post(String uri, Object body, UUID key, Object... variables) {
    try {
      JsonNode node =
          client
              .post()
              .uri(uri, variables)
              .header(HttpHeaders.AUTHORIZATION, authorization)
              .header("Idempotency-Key", key.toString())
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(JsonNode.class);
      return parse(node);
    } catch (ResourceAccessException exception) {
      throw new PaymentProviderException(
          "Toss Payments response was not received.", true, exception);
    } catch (RestClientResponseException exception) {
      throw new PaymentProviderException(
          "Toss Payments rejected the request ("
              + safeCode(exception.getResponseBodyAsString())
              + ").",
          exception.getStatusCode().is5xxServerError(),
          exception);
    }
  }

  private GatewayPayment get(String uri, Object variable) {
    try {
      return parse(
          client
              .get()
              .uri(uri, variable)
              .header(HttpHeaders.AUTHORIZATION, authorization)
              .retrieve()
              .body(JsonNode.class));
    } catch (ResourceAccessException exception) {
      throw new PaymentProviderException("Toss Payments lookup timed out.", true, exception);
    } catch (RestClientResponseException exception) {
      throw new PaymentProviderException("Toss Payments lookup failed.", false, exception);
    }
  }

  private GatewayPayment parse(JsonNode node) {
    if (node == null)
      throw new PaymentProviderException("Empty Toss Payments response.", true, null);
    String paymentKey = text(node, "paymentKey");
    String orderId = text(node, "orderId");
    String status = text(node, "status");
    long totalAmount = node.path("totalAmount").asLong(-1);
    long balanceAmount = node.path("balanceAmount").asLong(totalAmount);
    String approved = node.path("approvedAt").asText(null);
    LocalDateTime approvedAt =
        approved == null ? LocalDateTime.now() : OffsetDateTime.parse(approved).toLocalDateTime();
    String receiptUrl = node.path("receipt").path("url").asText(null);
    Map<String, Object> safe = new LinkedHashMap<>();
    safe.put("paymentKey", paymentKey);
    safe.put("orderId", orderId);
    safe.put("status", status);
    safe.put("method", node.path("method").asText(null));
    safe.put("totalAmount", totalAmount);
    safe.put("balanceAmount", balanceAmount);
    safe.put("approvedAt", approved);
    safe.put("receiptUrl", receiptUrl);
    return new GatewayPayment(
        paymentKey,
        orderId,
        status,
        node.path("method").asText(null),
        totalAmount,
        node.hasNonNull("suppliedAmount") ? node.get("suppliedAmount").asLong() : null,
        balanceAmount,
        approvedAt,
        receiptUrl,
        Collections.unmodifiableMap(safe));
  }

  private String text(JsonNode node, String field) {
    String value = node.path(field).asText(null);
    if (value == null || value.isBlank()) {
      throw new PaymentProviderException("Invalid Toss Payments response.", true, null);
    }
    return value;
  }

  private String safeCode(String body) {
    if (body == null) return "UNKNOWN";
    int marker = body.indexOf("\"code\"");
    if (marker < 0) return "UNKNOWN";
    String tail = body.substring(marker, Math.min(body.length(), marker + 100));
    return tail.replaceAll("[^A-Za-z0-9_:-]", "");
  }
}
