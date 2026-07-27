package com.finrisk.radar.admin;

import com.finrisk.radar.payment.PaymentOrderStatus;
import com.finrisk.radar.subscription.*;
import com.finrisk.radar.user.Role;
import java.time.LocalDateTime;

record AdminUserItem(
    Long userId,
    String email,
    String name,
    Role role,
    PlanType plan,
    LocalDateTime joinedAt,
    boolean activeSubscription,
    LocalDateTime subscriptionEndAt,
    String latestPaymentOrderId,
    PaymentOrderStatus latestPaymentStatus,
    LocalDateTime latestPaymentAt) {}

record AdminSubscriptionItem(
    Long subscriptionId,
    Long userId,
    String email,
    String name,
    PlanType plan,
    SubscriptionStatus status,
    LocalDateTime currentPeriodStart,
    LocalDateTime currentPeriodEnd,
    String publicOrderId) {}
