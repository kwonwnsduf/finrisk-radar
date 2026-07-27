package com.finrisk.radar.payment;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "payment_order_id")
  private Long paymentOrderId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "request_id", nullable = false, unique = true)
  private UUID requestId;

  @Column(name = "idempotency_key")
  private UUID idempotencyKey;

  @Column(name = "attempt_type", nullable = false, length = 30)
  private String attemptType;

  @Column(nullable = false, length = 30)
  private String result;

  @Column(name = "request_fingerprint", length = 64)
  private String requestFingerprint;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_payload", columnDefinition = "jsonb")
  private Map<String, Object> responsePayload;

  @Column(name = "error_code", length = 80)
  private String errorCode;

  @Column(name = "error_message", length = 500)
  private String errorMessage;

  @Column(name = "client_ip", length = 64)
  private String clientIp;

  @Column(name = "user_agent", length = 500)
  private String userAgent;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  protected PaymentAttempt() {}

  public static PaymentAttempt started(
      Long orderId,
      Long userId,
      UUID requestId,
      UUID idempotencyKey,
      String type,
      String fingerprint,
      String clientIp,
      String userAgent,
      LocalDateTime now) {
    PaymentAttempt value = new PaymentAttempt();
    value.paymentOrderId = orderId;
    value.userId = userId;
    value.requestId = requestId;
    value.idempotencyKey = idempotencyKey;
    value.attemptType = type;
    value.result = "STARTED";
    value.requestFingerprint = fingerprint;
    value.clientIp = clientIp;
    value.userAgent = userAgent;
    value.createdAt = now;
    return value;
  }

  public void complete(
      String result, Map<String, Object> response, String code, String message, LocalDateTime now) {
    this.result = result;
    this.responsePayload = response;
    this.errorCode = code;
    this.errorMessage = message;
    this.completedAt = now;
  }

  public Long getId() {
    return id;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public String getResult() {
    return result;
  }

  public Map<String, Object> getResponsePayload() {
    return responsePayload;
  }

  public String getClientIp() {
    return clientIp;
  }
}
