package com.finrisk.radar.payment;

import com.finrisk.radar.global.error.*;
import com.finrisk.radar.payment.outbox.OutboxService;
import com.finrisk.radar.subscription.*;
import com.finrisk.radar.user.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
class PaymentPersistenceService {
  private final PaymentOrderRepository orders;
  private final PaymentTransactionRepository transactions;
  private final PaymentCancellationRepository cancellations;
  private final PaymentAttemptRepository attempts;
  private final SubscriptionService subscriptions;
  private final UserRepository users;
  private final OutboxService outbox;

  PaymentPersistenceService(
      PaymentOrderRepository orders,
      PaymentTransactionRepository transactions,
      PaymentCancellationRepository cancellations,
      PaymentAttemptRepository attempts,
      SubscriptionService subscriptions,
      UserRepository users,
      OutboxService outbox) {
    this.orders = orders;
    this.transactions = transactions;
    this.cancellations = cancellations;
    this.attempts = attempts;
    this.subscriptions = subscriptions;
    this.users = users;
    this.outbox = outbox;
  }

  @Transactional
  PaymentOrder createOrder(Long userId) {
    User user =
        users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    if (user.getPlan() == PlanType.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
    String token = UUID.randomUUID().toString();
    return orders.save(PaymentOrder.premium(userId, "fr_" + token.replace("-", ""), token));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  PaymentAttempt startAttempt(
      Long orderId,
      Long userId,
      UUID idempotencyKey,
      String type,
      String fingerprint,
      PaymentRequestMetadata metadata) {
    return attempts.saveAndFlush(
        PaymentAttempt.started(
            orderId,
            userId,
            metadata.requestId(),
            idempotencyKey,
            type,
            fingerprint,
            metadata.ipHash(),
            metadata.userAgent(),
            LocalDateTime.now()));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void completeAttempt(
      Long attemptId, String result, Map<String, Object> response, ErrorCode error) {
    attempts
        .findById(attemptId)
        .ifPresent(
            value ->
                value.complete(
                    result,
                    response,
                    error == null ? null : error.getCode(),
                    error == null ? null : error.getMessage(),
                    LocalDateTime.now()));
  }

  @Transactional
  PaymentOrder claimConfirmation(String orderId, Long userId) {
    PaymentOrder order = requireOwnedForUpdate(orderId, userId);
    try {
      order.beginConfirmation(LocalDateTime.now());
    } catch (IllegalStateException exception) {
      throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
    }
    return order;
  }

  @Transactional
  PaymentOrder claimCancellation(String orderId, Long userId, LocalDateTime now) {
    PaymentOrder order = requireOwnedForUpdate(orderId, userId);
    subscriptions.assertCancellable(order, now);
    try {
      order.beginCancellation();
    } catch (IllegalStateException exception) {
      throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
    }
    return order;
  }

  @Transactional
  FinalizedPayment finalizePayment(String orderId, GatewayPayment payment, boolean recovered) {
    PaymentOrder order =
        orders
            .findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
    Optional<PaymentTransaction> existing = transactions.findByPaymentOrderId(order.getId());
    if (existing.isPresent()) {
      SubscriptionResponse subscription = subscriptions.getCurrent(order.getUserId());
      return new FinalizedPayment(order, existing.get(), null, subscription.currentPeriodEnd());
    }
    validate(order, payment);
    PaymentTransaction transaction;
    try {
      transaction = transactions.saveAndFlush(PaymentTransaction.from(order.getId(), payment));
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.PAYMENT_DUPLICATE_KEY);
    }
    order.paid(payment.approvedAt());
    SubscriptionService.EntitlementChange entitlement =
        subscriptions.activate(order, payment.approvedAt());
    Map<String, Object> payload = basePayload(order, transaction.getId(), payment.totalAmount());
    payload.put("recovered", recovered);
    outbox.append(
        order.getOrderId(),
        recovered ? "PaymentRecoveryCompletedEvent" : "PaymentCompletedEvent",
        payload);
    outbox.append(order.getOrderId(), "SubscriptionActivatedEvent", payload);
    return new FinalizedPayment(order, transaction, entitlement, entitlement.periodEnd());
  }

  @Transactional
  FinalizedCancellation finalizeCancellation(
      String orderId,
      UUID cancelRequestId,
      String reason,
      GatewayPayment payment,
      LocalDateTime operationTime,
      boolean recovered) {
    PaymentOrder order =
        orders
            .findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
    Optional<PaymentCancellation> existing = cancellations.findByPaymentOrderId(order.getId());
    if (existing.isPresent() && order.getStatus() == PaymentOrderStatus.CANCELED) {
      SubscriptionResponse subscription = subscriptions.getCurrent(order.getUserId());
      SubscriptionService.CanceledContribution contribution =
          subscriptions.canceledContribution(order);
      return new FinalizedCancellation(
          order,
          contribution.removedUnusedSeconds(),
          contribution.usedUntil(),
          subscription.currentPeriodEnd());
    }
    if (!payment.fullyCanceled() || payment.totalAmount() != order.getAmount()) {
      throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR);
    }
    SubscriptionService.EntitlementChange change =
        subscriptions.cancelContribution(order, operationTime);
    cancellations.save(
        PaymentCancellation.completed(
            order.getId(), cancelRequestId, reason, order.getAmount(), payment, operationTime));
    order.canceled(operationTime);
    SubscriptionService.CanceledContribution contribution =
        subscriptions.canceledContribution(order);
    Map<String, Object> payload = basePayload(order, null, order.getAmount());
    payload.put("removedUnusedSeconds", change.removedUnusedSeconds());
    payload.put("contributionPeriodStart", contribution.periodStart().toString());
    payload.put("contributionUsedUntil", contribution.usedUntil().toString());
    payload.put("recovered", recovered);
    outbox.append(order.getOrderId(), "PaymentCanceledEvent", payload);
    return new FinalizedCancellation(
        order, change.removedUnusedSeconds(), contribution.usedUntil(), change.periodEnd());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void markFailed(String orderId) {
    orders
        .findByOrderIdForUpdate(orderId)
        .ifPresent(
            order -> {
              order.failed(LocalDateTime.now());
              outbox.append(
                  order.getOrderId(),
                  "PaymentFailedEvent",
                  basePayload(order, null, order.getAmount()));
            });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void markRecoveryRequired(String orderId) {
    orders.findByOrderIdForUpdate(orderId).ifPresent(PaymentOrder::recoveryRequired);
  }

  private void validate(PaymentOrder order, GatewayPayment payment) {
    if (!payment.paid()
        || !order.getOrderId().equals(payment.orderId())
        || order.getAmount() != payment.totalAmount()) {
      throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR);
    }
  }

  private PaymentOrder requireOwnedForUpdate(String orderId, Long userId) {
    PaymentOrder order =
        orders
            .findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
    if (!order.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.PAYMENT_ORDER_FORBIDDEN);
    }
    return order;
  }

  private Map<String, Object> basePayload(PaymentOrder order, Long paymentId, long amount) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("paymentOrderId", order.getId());
    payload.put("orderId", order.getOrderId());
    payload.put("paymentId", paymentId);
    payload.put("userId", order.getUserId());
    payload.put("amount", amount);
    payload.put("provider", order.getProvider());
    return payload;
  }

  record FinalizedPayment(
      PaymentOrder order,
      PaymentTransaction transaction,
      SubscriptionService.EntitlementChange entitlement,
      LocalDateTime subscriptionEnd) {}

  record FinalizedCancellation(
      PaymentOrder order,
      long removedUnusedSeconds,
      LocalDateTime usedUntil,
      LocalDateTime subscriptionEnd) {}
}
