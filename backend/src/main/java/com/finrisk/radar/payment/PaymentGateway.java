package com.finrisk.radar.payment;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public interface PaymentGateway {
  GatewayPayment confirmPayment(
      String paymentKey, String orderId, long amount, UUID idempotencyKey);

  GatewayPayment cancelPayment(String paymentKey, String reason, UUID idempotencyKey);

  GatewayPayment getPayment(String paymentKey);

  GatewayPayment getPaymentByOrderId(String orderId);
}

record GatewayPayment(
    String paymentKey,
    String orderId,
    String status,
    String method,
    long totalAmount,
    Long suppliedAmount,
    long balanceAmount,
    LocalDateTime approvedAt,
    String receiptUrl,
    Map<String, Object> safePayload) {
  boolean paid() {
    return "DONE".equals(status);
  }

  boolean fullyCanceled() {
    return "CANCELED".equals(status) && balanceAmount == 0;
  }
}

class PaymentProviderException extends RuntimeException {
  private final boolean ambiguous;

  PaymentProviderException(String message, boolean ambiguous, Throwable cause) {
    super(message, cause);
    this.ambiguous = ambiguous;
  }

  boolean ambiguous() {
    return ambiguous;
  }
}

final class DisabledPaymentGateway implements PaymentGateway {
  private PaymentProviderException disabled() {
    return new PaymentProviderException("Payment feature is not configured.", false, null);
  }

  public GatewayPayment confirmPayment(String p, String o, long a, UUID i) {
    throw disabled();
  }

  public GatewayPayment cancelPayment(String p, String r, UUID i) {
    throw disabled();
  }

  public GatewayPayment getPayment(String p) {
    throw disabled();
  }

  public GatewayPayment getPaymentByOrderId(String o) {
    throw disabled();
  }
}
