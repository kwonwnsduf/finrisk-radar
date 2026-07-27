package com.finrisk.radar.subscription;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
public class Subscription extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, unique = true)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PlanType plan;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SubscriptionStatus status;

  @Column(name = "current_period_start")
  private LocalDateTime currentPeriodStart;

  @Column(name = "current_period_end")
  private LocalDateTime currentPeriodEnd;

  @Column(name = "activated_by_payment_order_id")
  private Long activatedByPaymentOrderId;

  @Column(name = "canceled_at")
  private LocalDateTime canceledAt;

  @Version private long version;

  protected Subscription() {}

  static Subscription create(Long userId) {
    Subscription value = new Subscription();
    value.userId = userId;
    value.plan = PlanType.FREE;
    value.status = SubscriptionStatus.EXPIRED;
    return value;
  }

  void project(LocalDateTime start, LocalDateTime end, Long activeOrderId, LocalDateTime now) {
    currentPeriodStart = start;
    currentPeriodEnd = end;
    activatedByPaymentOrderId = activeOrderId;
    if (end != null && end.isAfter(now)) {
      plan = PlanType.PREMIUM;
      status = SubscriptionStatus.ACTIVE;
      canceledAt = null;
    } else {
      plan = PlanType.FREE;
      status = SubscriptionStatus.CANCELED;
      canceledAt = now;
    }
  }

  void expire(LocalDateTime now) {
    plan = PlanType.FREE;
    status = SubscriptionStatus.EXPIRED;
    activatedByPaymentOrderId = null;
    currentPeriodEnd = currentPeriodEnd == null ? now : currentPeriodEnd;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public PlanType getPlan() {
    return plan;
  }

  public SubscriptionStatus getStatus() {
    return status;
  }

  public LocalDateTime getCurrentPeriodStart() {
    return currentPeriodStart;
  }

  public LocalDateTime getCurrentPeriodEnd() {
    return currentPeriodEnd;
  }

  public Long getActivatedByPaymentOrderId() {
    return activatedByPaymentOrderId;
  }
}

enum SubscriptionStatus {
  ACTIVE,
  CANCELED,
  EXPIRED
}
