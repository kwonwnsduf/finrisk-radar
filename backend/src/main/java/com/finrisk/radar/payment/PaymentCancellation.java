package com.finrisk.radar.payment;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_cancellations")
public class PaymentCancellation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "payment_order_id", nullable = false, unique = true)
  private Long paymentOrderId;

  @Column(name = "cancel_request_id", nullable = false, unique = true)
  private UUID cancelRequestId;

  @Column(name = "cancel_reason", nullable = false, length = 200)
  private String cancelReason;

  @Column(nullable = false)
  private long amount;

  @Column(nullable = false, length = 20)
  private String status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "provider_response", columnDefinition = "jsonb")
  private Map<String, Object> providerResponse;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "failed_at")
  private LocalDateTime failedAt;

  protected PaymentCancellation() {}

  public static PaymentCancellation completed(
      Long orderId,
      UUID requestId,
      String reason,
      long amount,
      GatewayPayment payment,
      LocalDateTime now) {
    PaymentCancellation value = new PaymentCancellation();
    value.paymentOrderId = orderId;
    value.cancelRequestId = requestId;
    value.cancelReason = reason;
    value.amount = amount;
    value.status = "COMPLETED";
    value.providerResponse = payment.safePayload();
    value.requestedAt = now;
    value.completedAt = now;
    return value;
  }

  public UUID getCancelRequestId() {
    return cancelRequestId;
  }

  public Long getPaymentOrderId() {
    return paymentOrderId;
  }
}
