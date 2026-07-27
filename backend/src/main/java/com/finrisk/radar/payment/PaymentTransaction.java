package com.finrisk.radar.payment;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "payment_order_id", nullable = false, unique = true)
  private Long paymentOrderId;

  @Column(name = "payment_key", nullable = false, unique = true, length = 200)
  private String paymentKey;

  @Column(nullable = false, length = 20)
  private String provider;

  @Column(name = "provider_status", nullable = false, length = 30)
  private String providerStatus;

  @Column(length = 50)
  private String method;

  @Column(name = "total_amount", nullable = false)
  private long totalAmount;

  @Column(name = "supplied_amount")
  private Long suppliedAmount;

  @Column(name = "approved_at", nullable = false)
  private LocalDateTime approvedAt;

  @Column(name = "receipt_url", length = 1000)
  private String receiptUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_response", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> rawResponse;

  protected PaymentTransaction() {}

  public static PaymentTransaction from(Long orderId, GatewayPayment payment) {
    PaymentTransaction value = new PaymentTransaction();
    value.paymentOrderId = orderId;
    value.paymentKey = payment.paymentKey();
    value.provider = "TOSS";
    value.providerStatus = payment.status();
    value.method = payment.method();
    value.totalAmount = payment.totalAmount();
    value.suppliedAmount = payment.suppliedAmount();
    value.approvedAt = payment.approvedAt();
    value.receiptUrl = payment.receiptUrl();
    value.rawResponse = payment.safePayload();
    return value;
  }

  public Long getId() {
    return id;
  }

  public Long getPaymentOrderId() {
    return paymentOrderId;
  }

  public String getPaymentKey() {
    return paymentKey;
  }

  public String getMethod() {
    return method;
  }

  public long getTotalAmount() {
    return totalAmount;
  }

  public LocalDateTime getApprovedAt() {
    return approvedAt;
  }

  public String getReceiptUrl() {
    return receiptUrl;
  }
}
