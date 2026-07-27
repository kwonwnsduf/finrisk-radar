package com.finrisk.radar.subscription;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "subscription_entitlements")
public class SubscriptionEntitlement extends BaseTimeEntity {
  public static final long DURATION_SECONDS = 2_592_000L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "subscription_id", nullable = false)
  private Long subscriptionId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "payment_order_id", nullable = false, unique = true)
  private Long paymentOrderId;

  @Column(name = "original_duration_seconds", nullable = false)
  private long originalDurationSeconds;

  @Column(name = "period_start", nullable = false)
  private LocalDateTime periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDateTime periodEnd;

  @Column(name = "used_until")
  private LocalDateTime usedUntil;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EntitlementStatus status;

  @Column(name = "canceled_at")
  private LocalDateTime canceledAt;

  @Column(name = "removed_unused_seconds", nullable = false)
  private long removedUnusedSeconds;

  @Version private long version;

  protected SubscriptionEntitlement() {}

  static SubscriptionEntitlement create(
      Long subscriptionId, Long userId, Long orderId, LocalDateTime start, LocalDateTime now) {
    SubscriptionEntitlement value = new SubscriptionEntitlement();
    value.subscriptionId = subscriptionId;
    value.userId = userId;
    value.paymentOrderId = orderId;
    value.originalDurationSeconds = DURATION_SECONDS;
    value.periodStart = start;
    value.periodEnd = start.plusSeconds(DURATION_SECONDS);
    value.removedUnusedSeconds = 0;
    value.refresh(now);
    return value;
  }

  long cancelUnused(LocalDateTime now) {
    LocalDateTime effectiveStart = now.isAfter(periodStart) ? now : periodStart;
    long unused = Math.max(0, Duration.between(effectiveStart, periodEnd).getSeconds());
    if (unused == 0) return 0;
    usedUntil = now.isBefore(periodStart) ? periodStart : now;
    canceledAt = now;
    removedUnusedSeconds = unused;
    status = EntitlementStatus.CANCELED;
    return unused;
  }

  void shiftEarlier(long seconds, LocalDateTime now) {
    periodStart = periodStart.minusSeconds(seconds);
    periodEnd = periodEnd.minusSeconds(seconds);
    refresh(now);
  }

  void refresh(LocalDateTime now) {
    if (status == EntitlementStatus.CANCELED) return;
    if (!now.isBefore(periodEnd)) {
      status = EntitlementStatus.CONSUMED;
      usedUntil = periodEnd;
    } else if (now.isBefore(periodStart)) {
      status = EntitlementStatus.SCHEDULED;
    } else {
      status = EntitlementStatus.ACTIVE;
      usedUntil = now;
    }
  }

  boolean contains(LocalDateTime now) {
    return status != EntitlementStatus.CANCELED
        && !now.isBefore(periodStart)
        && now.isBefore(periodEnd);
  }

  long remaining(LocalDateTime now) {
    if (status == EntitlementStatus.CANCELED || !periodEnd.isAfter(now)) return 0;
    LocalDateTime start = now.isAfter(periodStart) ? now : periodStart;
    return Duration.between(start, periodEnd).getSeconds();
  }

  public Long getId() {
    return id;
  }

  public Long getPaymentOrderId() {
    return paymentOrderId;
  }

  public LocalDateTime getPeriodStart() {
    return periodStart;
  }

  public LocalDateTime getPeriodEnd() {
    return periodEnd;
  }

  public LocalDateTime getUsedUntil() {
    return usedUntil;
  }

  public LocalDateTime getCanceledAt() {
    return canceledAt;
  }

  public long getRemovedUnusedSeconds() {
    return removedUnusedSeconds;
  }

  public EntitlementStatus getStatus() {
    return status;
  }
}

enum EntitlementStatus {
  SCHEDULED,
  ACTIVE,
  CONSUMED,
  CANCELED
}
