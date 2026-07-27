package com.finrisk.radar.payment;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_orders")
public class PaymentOrder extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false, unique = true, length = 64, updatable = false)
  private String orderId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "product_code", nullable = false, length = 40, updatable = false)
  private String productCode;

  @Column(name = "order_name", nullable = false, length = 100, updatable = false)
  private String orderName;

  @Column(nullable = false, updatable = false)
  private long amount;

  @Column(nullable = false, length = 3, updatable = false)
  private String currency;

  @Column(nullable = false, length = 20, updatable = false)
  private String provider;

  @Column(name = "customer_key", nullable = false, length = 64, updatable = false)
  private String customerKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PaymentOrderStatus status;

  private LocalDateTime confirmingAt;
  private LocalDateTime paidAt;
  private LocalDateTime canceledAt;
  private LocalDateTime failedAt;

  @Version private long version;

  protected PaymentOrder() {}

  public static PaymentOrder premium(Long userId, String orderId, String customerKey) {
    PaymentOrder value = new PaymentOrder();
    value.userId = userId;
    value.orderId = orderId;
    value.customerKey = customerKey;
    value.productCode = PaymentProduct.PREMIUM_MONTHLY.code();
    value.orderName = PaymentProduct.PREMIUM_MONTHLY.orderName();
    value.amount = PaymentProduct.PREMIUM_MONTHLY.amount();
    value.currency = "KRW";
    value.provider = "TOSS";
    value.status = PaymentOrderStatus.READY;
    return value;
  }

  public void beginConfirmation(LocalDateTime now) {
    require(PaymentOrderStatus.READY);
    status = PaymentOrderStatus.CONFIRMING;
    confirmingAt = now;
  }

  public void paid(LocalDateTime now) {
    if (status != PaymentOrderStatus.CONFIRMING && status != PaymentOrderStatus.RECOVERY_REQUIRED) {
      throw new IllegalStateException("Payment order cannot be paid from " + status);
    }
    status = PaymentOrderStatus.PAID;
    paidAt = now;
    failedAt = null;
  }

  public void beginCancellation() {
    require(PaymentOrderStatus.PAID);
    status = PaymentOrderStatus.CANCELING;
  }

  public void canceled(LocalDateTime now) {
    if (status != PaymentOrderStatus.CANCELING && status != PaymentOrderStatus.RECOVERY_REQUIRED) {
      throw new IllegalStateException("Payment order cannot be canceled from " + status);
    }
    status = PaymentOrderStatus.CANCELED;
    canceledAt = now;
  }

  public void failed(LocalDateTime now) {
    status = PaymentOrderStatus.FAILED;
    failedAt = now;
  }

  public void recoveryRequired() {
    status = PaymentOrderStatus.RECOVERY_REQUIRED;
  }

  private void require(PaymentOrderStatus expected) {
    if (status != expected)
      throw new IllegalStateException("Expected " + expected + " but was " + status);
  }

  public Long getId() {
    return id;
  }

  public String getOrderId() {
    return orderId;
  }

  public Long getUserId() {
    return userId;
  }

  public String getProductCode() {
    return productCode;
  }

  public String getOrderName() {
    return orderName;
  }

  public long getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getProvider() {
    return provider;
  }

  public String getCustomerKey() {
    return customerKey;
  }

  public PaymentOrderStatus getStatus() {
    return status;
  }

  public LocalDateTime getPaidAt() {
    return paidAt;
  }

  public LocalDateTime getCanceledAt() {
    return canceledAt;
  }
}
