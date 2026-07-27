package com.finrisk.radar.subscription;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.payment.PaymentOrder;
import com.finrisk.radar.payment.PaymentOrderLookupService;
import com.finrisk.radar.payment.outbox.OutboxService;
import com.finrisk.radar.user.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {
  private final SubscriptionRepository subscriptions;
  private final SubscriptionEntitlementRepository entitlements;
  private final UserRepository users;
  private final PaymentOrderLookupService paymentOrders;
  private final OutboxService outbox;

  public SubscriptionService(
      SubscriptionRepository subscriptions,
      SubscriptionEntitlementRepository entitlements,
      UserRepository users,
      PaymentOrderLookupService paymentOrders,
      OutboxService outbox) {
    this.subscriptions = subscriptions;
    this.entitlements = entitlements;
    this.users = users;
    this.paymentOrders = paymentOrders;
    this.outbox = outbox;
  }

  @Transactional
  public EntitlementChange activate(PaymentOrder order, LocalDateTime now) {
    Subscription subscription =
        subscriptions
            .findByUserIdForUpdate(order.getUserId())
            .orElseGet(() -> subscriptions.saveAndFlush(Subscription.create(order.getUserId())));
    List<SubscriptionEntitlement> values = entitlements.findByUserIdForUpdate(order.getUserId());
    Optional<SubscriptionEntitlement> existing =
        values.stream().filter(e -> e.getPaymentOrderId().equals(order.getId())).findFirst();
    if (existing.isPresent()) return project(subscription, values, now, 0);

    LocalDateTime start =
        values.stream()
            .filter(e -> e.getStatus() != EntitlementStatus.CANCELED)
            .map(SubscriptionEntitlement::getPeriodEnd)
            .max(LocalDateTime::compareTo)
            .filter(end -> end.isAfter(now))
            .orElse(now);
    SubscriptionEntitlement created =
        entitlements.save(
            SubscriptionEntitlement.create(
                subscription.getId(), order.getUserId(), order.getId(), start, now));
    values.add(created);
    return project(subscription, values, now, 0);
  }

  @Transactional
  public EntitlementChange cancelContribution(PaymentOrder order, LocalDateTime now) {
    Subscription subscription =
        subscriptions
            .findByUserIdForUpdate(order.getUserId())
            .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    List<SubscriptionEntitlement> values = entitlements.findByUserIdForUpdate(order.getUserId());
    SubscriptionEntitlement target =
        values.stream()
            .filter(e -> e.getPaymentOrderId().equals(order.getId()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    if (target.getStatus() == EntitlementStatus.CANCELED)
      return project(subscription, values, now, 0);

    long unused = target.remaining(now);
    if (unused == 0) throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ELIGIBLE);
    target.cancelUnused(now);
    boolean later = false;
    for (SubscriptionEntitlement entitlement : values) {
      if (entitlement == target) {
        later = true;
      } else if (later && entitlement.getStatus() != EntitlementStatus.CANCELED) {
        entitlement.shiftEarlier(unused, now);
      }
    }
    return project(subscription, values, now, unused);
  }

  @Transactional
  public void assertCancellable(PaymentOrder order, LocalDateTime now) {
    SubscriptionEntitlement entitlement =
        entitlements
            .findByPaymentOrderId(order.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    if (entitlement.remaining(now) == 0) {
      throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ELIGIBLE);
    }
  }

  @Transactional(readOnly = true)
  public CanceledContribution canceledContribution(PaymentOrder order) {
    SubscriptionEntitlement entitlement =
        entitlements
            .findByPaymentOrderId(order.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    return new CanceledContribution(
        entitlement.getPeriodStart(),
        entitlement.getUsedUntil(),
        entitlement.getRemovedUnusedSeconds());
  }

  @Transactional(readOnly = true)
  public Contribution contribution(PaymentOrder order, LocalDateTime now) {
    return entitlements
        .findByPaymentOrderId(order.getId())
        .map(
            value ->
                new Contribution(
                    value.getPeriodStart(),
                    value.getPeriodEnd(),
                    value.remaining(now),
                    value.getUsedUntil(),
                    value.getRemovedUnusedSeconds()))
        .orElse(null);
  }

  @Transactional
  public SubscriptionResponse getCurrent(Long userId) {
    LocalDateTime now = LocalDateTime.now();
    expireUserIfNeeded(userId, now);
    User user =
        users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    Subscription subscription = subscriptions.findByUserId(userId).orElse(null);
    if (subscription == null) {
      return new SubscriptionResponse(
          user.getPlan().name(), "EXPIRED", null, null, 0, false, null, List.of());
    }
    List<SubscriptionEntitlement> values =
        entitlements.findByUserIdOrderByPeriodStartAscIdAsc(userId);
    return response(subscription, values, now);
  }

  @Scheduled(fixedDelayString = "${app.payment.subscription-expiry-delay:60000}")
  @Transactional
  public void expireDue() {
    LocalDateTime now = LocalDateTime.now();
    for (Subscription subscription :
        subscriptions.findTop100ByStatusAndCurrentPeriodEndBeforeOrderByCurrentPeriodEndAsc(
            SubscriptionStatus.ACTIVE, now)) {
      expireUserIfNeeded(subscription.getUserId(), now);
    }
  }

  private void expireUserIfNeeded(Long userId, LocalDateTime now) {
    subscriptions
        .findByUserIdForUpdate(userId)
        .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
        .filter(s -> s.getCurrentPeriodEnd() != null && !s.getCurrentPeriodEnd().isAfter(now))
        .ifPresent(
            subscription -> {
              subscription.expire(now);
              users.findByIdForUpdate(userId).ifPresent(user -> user.changePlan(PlanType.FREE));
              entitlements.findByUserIdForUpdate(userId).forEach(value -> value.refresh(now));
              outbox.append(
                  "subscription:" + userId,
                  "SubscriptionExpiredEvent",
                  Map.of(
                      "userId", userId,
                      "subscriptionId", subscription.getId(),
                      "expiredAt", now.toString()));
            });
  }

  private EntitlementChange project(
      Subscription subscription,
      List<SubscriptionEntitlement> values,
      LocalDateTime now,
      long removedSeconds) {
    values.forEach(value -> value.refresh(now));
    List<SubscriptionEntitlement> retained =
        values.stream().filter(e -> e.getStatus() != EntitlementStatus.CANCELED).toList();
    LocalDateTime start =
        retained.stream()
            .map(SubscriptionEntitlement::getPeriodStart)
            .min(LocalDateTime::compareTo)
            .orElse(null);
    LocalDateTime end =
        retained.stream()
            .map(SubscriptionEntitlement::getPeriodEnd)
            .max(LocalDateTime::compareTo)
            .orElse(null);
    Long activeOrder =
        retained.stream()
            .filter(e -> e.contains(now))
            .map(SubscriptionEntitlement::getPaymentOrderId)
            .findFirst()
            .orElse(null);
    subscription.project(start, end, activeOrder, now);
    users
        .findByIdForUpdate(subscription.getUserId())
        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED))
        .changePlan(end != null && end.isAfter(now) ? PlanType.PREMIUM : PlanType.FREE);
    return new EntitlementChange(start, end, activeOrder, removedSeconds);
  }

  private SubscriptionResponse response(
      Subscription subscription, List<SubscriptionEntitlement> values, LocalDateTime now) {
    Map<Long, String> orderIds =
        paymentOrders.publicOrderIds(
            values.stream().map(SubscriptionEntitlement::getPaymentOrderId).toList());
    List<SubscriptionResponse.EntitlementResponse> items =
        values.stream()
            .map(
                e ->
                    new SubscriptionResponse.EntitlementResponse(
                        orderIds.getOrDefault(
                            e.getPaymentOrderId(), e.getPaymentOrderId().toString()),
                        e.getPeriodStart(),
                        e.getPeriodEnd(),
                        e.getStatus().name(),
                        e.remaining(now),
                        e.getUsedUntil(),
                        e.getCanceledAt(),
                        e.getRemovedUnusedSeconds()))
            .toList();
    return new SubscriptionResponse(
        subscription.getPlan().name(),
        subscription.getStatus().name(),
        subscription.getCurrentPeriodStart(),
        subscription.getCurrentPeriodEnd(),
        subscription.getCurrentPeriodEnd() == null
            ? 0
            : Math.max(
                0,
                ChronoUnit.DAYS.between(
                    now.toLocalDate(), subscription.getCurrentPeriodEnd().toLocalDate())),
        false,
        subscription.getActivatedByPaymentOrderId() == null
            ? null
            : orderIds.getOrDefault(
                subscription.getActivatedByPaymentOrderId(),
                subscription.getActivatedByPaymentOrderId().toString()),
        items);
  }

  public record EntitlementChange(
      LocalDateTime periodStart,
      LocalDateTime periodEnd,
      Long activePaymentOrderId,
      long removedUnusedSeconds) {}

  public record CanceledContribution(
      LocalDateTime periodStart, LocalDateTime usedUntil, long removedUnusedSeconds) {}

  public record Contribution(
      LocalDateTime periodStart,
      LocalDateTime periodEnd,
      long remainingSeconds,
      LocalDateTime usedUntil,
      long removedUnusedSeconds) {}
}
