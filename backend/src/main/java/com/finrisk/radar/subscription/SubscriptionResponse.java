package com.finrisk.radar.subscription;

import java.time.LocalDateTime;
import java.util.List;

public record SubscriptionResponse(
    String currentPlan,
    String subscriptionStatus,
    LocalDateTime currentPeriodStart,
    LocalDateTime currentPeriodEnd,
    long remainingDays,
    boolean autoRenew,
    String activatedByOrderId,
    List<EntitlementResponse> entitlements) {

  public record EntitlementResponse(
      String orderId,
      LocalDateTime periodStart,
      LocalDateTime periodEnd,
      String status,
      long remainingSeconds,
      LocalDateTime usedUntil,
      LocalDateTime canceledAt,
      long removedUnusedSeconds) {}
}
