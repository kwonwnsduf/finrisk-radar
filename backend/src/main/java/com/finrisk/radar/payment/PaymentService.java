package com.finrisk.radar.payment;

import com.finrisk.radar.fsd.*;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.subscription.*;
import com.finrisk.radar.user.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
  private final PaymentOrderRepository orders;
  private final PaymentTransactionRepository transactions;
  private final PaymentCancellationRepository cancellations;
  private final PaymentAttemptRepository attempts;
  private final UserRepository users;
  private final SubscriptionService subscriptions;
  private final PaymentPersistenceService persistence;
  private final PaymentGateway gateway;
  private final PaymentLockService locks;
  private final PaymentProperties properties;
  private final FsdEngine fsd;
  private final FsdSignalStore fsdSignals;
  private final MeterRegistry meters;

  public PaymentService(
      PaymentOrderRepository orders,
      PaymentTransactionRepository transactions,
      PaymentCancellationRepository cancellations,
      PaymentAttemptRepository attempts,
      UserRepository users,
      SubscriptionService subscriptions,
      PaymentPersistenceService persistence,
      PaymentGateway gateway,
      PaymentLockService locks,
      PaymentProperties properties,
      FsdEngine fsd,
      FsdSignalStore fsdSignals,
      MeterRegistry meters) {
    this.orders = orders;
    this.transactions = transactions;
    this.cancellations = cancellations;
    this.attempts = attempts;
    this.users = users;
    this.subscriptions = subscriptions;
    this.persistence = persistence;
    this.gateway = gateway;
    this.locks = locks;
    this.properties = properties;
    this.fsd = fsd;
    this.fsdSignals = fsdSignals;
    this.meters = meters;
  }

  public PaymentOrderResponse createOrder(
      Long userId,
      CreateOrderRequest request,
      UUID idempotencyKey,
      PaymentRequestMetadata metadata) {
    try {
      PaymentProduct.require(request.productCode());
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
    String requestFingerprint = fingerprint(request.productCode());
    if (idempotencyKey != null) {
      Optional<PaymentAttempt> replay =
          attempts.findByUserIdAndAttemptTypeAndIdempotencyKey(
              userId, "ORDER_CREATE", idempotencyKey);
      if (replay.isPresent()) {
        if (!requestFingerprint.equals(replay.get().getRequestFingerprint())) {
          throw new BusinessException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
        }
        Object replayedOrderId =
            replay.get().getResponsePayload() == null
                ? null
                : replay.get().getResponsePayload().get("orderId");
        if (replayedOrderId instanceof String value) {
          return orderResponse(requireOwned(value, userId), userId);
        }
        throw new BusinessException(ErrorCode.PAYMENT_ORDER_IN_PROGRESS);
      }
    }
    PaymentAttempt attempt;
    try {
      attempt =
          persistence.startAttempt(
              null, userId, idempotencyKey, "ORDER_CREATE", requestFingerprint, metadata);
    } catch (DataIntegrityViolationException concurrentRequest) {
      throw new BusinessException(ErrorCode.PAYMENT_ORDER_IN_PROGRESS);
    }
    LocalDateTime after = LocalDateTime.now().minus(fsd.properties().rapidOrderCreation().window());
    long recent;
    try {
      recent = fsdSignals.recordOrder(userId, Instant.now());
    } catch (RuntimeException redisUnavailable) {
      recent =
          orders.countByUserIdAndStatusAndCreatedAtAfter(userId, PaymentOrderStatus.READY, after)
              + 1;
    }
    if (fsd.properties().enabled()
        && fsd.properties().rapidOrderCreation().control().enabled()
        && recent > fsd.properties().rapidOrderCreation().maxOrders()) {
      fsd.recordRapidOrderBlock(userId, attempt.getId(), (int) recent);
      persistence.completeAttempt(
          attempt.getId(), "BLOCKED", null, ErrorCode.PAYMENT_ORDER_RATE_LIMITED);
      throw new BusinessException(ErrorCode.PAYMENT_ORDER_RATE_LIMITED);
    }
    PaymentOrder order = persistence.createOrder(userId);
    persistence.completeAttempt(
        attempt.getId(), "SUCCEEDED", Map.of("orderId", order.getOrderId()), null);
    meters.counter("payment.order.created").increment();
    return orderResponse(order, userId);
  }

  private PaymentOrderResponse orderResponse(PaymentOrder order, Long userId) {
    User user =
        users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    String base = properties.frontendBaseUrl().replaceAll("/+$", "");
    return new PaymentOrderResponse(
        order.getOrderId(),
        order.getOrderName(),
        order.getAmount(),
        order.getCurrency(),
        order.getCustomerKey(),
        user.getName(),
        base + "/payment/success",
        base + "/payment/fail");
  }

  public PaymentResultResponse confirm(
      Long userId, ConfirmPaymentRequest request, PaymentRequestMetadata metadata) {
    PaymentOrder order = requireOrder(request.orderId());
    if (!order.getUserId().equals(userId))
      throw new BusinessException(ErrorCode.PAYMENT_ORDER_FORBIDDEN);
    if (request.amount() != order.getAmount())
      throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

    Optional<PaymentTransaction> completed = transactions.findByPaymentOrderId(order.getId());
    if (completed.isPresent()) {
      if (!completed.get().getPaymentKey().equals(request.paymentKey())) {
        throw new BusinessException(ErrorCode.PAYMENT_DUPLICATE_KEY);
      }
      return result(order, completed.get(), subscriptions.getCurrent(userId), true, false);
    }

    String fingerprint =
        fingerprint(request.orderId(), request.paymentKey(), Long.toString(request.amount()));
    Optional<PaymentAttempt> replay =
        attempts.findByUserIdAndAttemptTypeAndIdempotencyKey(
            userId, "CONFIRM", request.idempotencyKey());
    if (replay.isPresent() && !fingerprint.equals(replay.get().getRequestFingerprint())) {
      throw new BusinessException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
    }
    PaymentAttempt attempt =
        replay.orElseGet(
            () ->
                persistence.startAttempt(
                    order.getId(),
                    userId,
                    request.idempotencyKey(),
                    "CONFIRM",
                    fingerprint,
                    metadata));

    int failures;
    int ipUsers;
    try {
      failures = Math.toIntExact(fsdSignals.recentFailures(userId, Instant.now()));
      ipUsers =
          Math.toIntExact(fsdSignals.recordIpAccount(metadata.ipHash(), userId, Instant.now()));
    } catch (RuntimeException redisUnavailable) {
      failures =
          (int)
              attempts.countByUserIdAndResultAndCreatedAtAfter(
                  userId,
                  "FAILED",
                  LocalDateTime.now().minus(fsd.properties().failureBurst().window()));
      ipUsers =
          (int)
              attempts.countDistinctUsersByClientIp(
                  metadata.ipHash(),
                  LocalDateTime.now().minus(fsd.properties().sameIpAccounts().window()));
    }
    FsdDecision decision =
        fsd.evaluatePreConfirm(
            new FsdEngine.PreConfirmContext(
                order.getId(),
                attempt.getId(),
                userId,
                order.getUserId(),
                request.amount(),
                order.getAmount(),
                transactions.existsByPaymentKeyAndPaymentOrderIdNot(
                    request.paymentKey(), order.getId()),
                order.getStatus() != PaymentOrderStatus.READY,
                failures,
                ipUsers,
                metadata.clientRequestIdPresent(),
                metadata.userAgent() != null && !metadata.userAgent().isBlank()));
    if (decision == FsdDecision.BLOCK) {
      persistence.completeAttempt(attempt.getId(), "BLOCKED", null, ErrorCode.PAYMENT_FSD_BLOCKED);
      meters.counter("payment.confirm.blocked").increment();
      throw new BusinessException(ErrorCode.PAYMENT_FSD_BLOCKED);
    }

    PaymentLockService.LockHandle lock = locks.acquire("confirm", order.getOrderId());
    if (!lock.acquired()) throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_IN_PROGRESS);
    try {
      persistence.claimConfirmation(order.getOrderId(), userId);
      GatewayPayment payment;
      boolean recovered = false;
      try {
        payment =
            gateway.confirmPayment(
                request.paymentKey(),
                order.getOrderId(),
                order.getAmount(),
                request.idempotencyKey());
      } catch (PaymentProviderException exception) {
        if (!exception.ambiguous()) {
          recordFailureSignal(userId);
          persistence.markFailed(order.getOrderId());
          persistence.completeAttempt(
              attempt.getId(), "FAILED", null, ErrorCode.PAYMENT_PROVIDER_ERROR);
          meters.counter("payment.confirm.failed").increment();
          throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        payment = lookupOrRecovery(order.getOrderId(), request.paymentKey());
        recovered = true;
      }
      PaymentPersistenceService.FinalizedPayment finalized =
          persistence.finalizePayment(order.getOrderId(), payment, recovered);
      persistence.completeAttempt(
          attempt.getId(), "SUCCEEDED", Map.of("orderId", order.getOrderId()), null);
      meters
          .counter(recovered ? "payment.recovery.success" : "payment.confirm.success")
          .increment();
      return result(
          finalized.order(),
          finalized.transaction(),
          subscriptions.getCurrent(userId),
          false,
          false);
    } finally {
      locks.release(lock);
    }
  }

  public PaymentCancelResponse cancel(
      Long userId, String orderId, CancelPaymentRequest request, PaymentRequestMetadata metadata) {
    PaymentOrder order = requireOwned(orderId, userId);
    Optional<PaymentCancellation> existing =
        cancellations.findByCancelRequestId(request.cancelRequestId());
    if (existing.isPresent() && !existing.get().getPaymentOrderId().equals(order.getId())) {
      throw new BusinessException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
    }
    if (existing.isPresent() && order.getStatus() == PaymentOrderStatus.CANCELED) {
      SubscriptionService.CanceledContribution contribution =
          subscriptions.canceledContribution(order);
      return cancelResult(
          order,
          subscriptions.getCurrent(userId),
          contribution.removedUnusedSeconds(),
          contribution.usedUntil(),
          true);
    }
    PaymentTransaction transaction =
        transactions
            .findByPaymentOrderId(order.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS));
    subscriptions.assertCancellable(order, LocalDateTime.now());
    String fingerprint = fingerprint(orderId, request.reason());
    Optional<PaymentAttempt> replay =
        attempts.findByUserIdAndAttemptTypeAndIdempotencyKey(
            userId, "CANCEL", request.cancelRequestId());
    if (replay.isPresent() && !fingerprint.equals(replay.get().getRequestFingerprint())) {
      throw new BusinessException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
    }
    PaymentAttempt attempt =
        replay.orElseGet(
            () ->
                persistence.startAttempt(
                    order.getId(),
                    userId,
                    request.cancelRequestId(),
                    "CANCEL",
                    fingerprint,
                    metadata));
    PaymentLockService.LockHandle lock = locks.acquire("cancel", orderId);
    if (!lock.acquired()) throw new BusinessException(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
    LocalDateTime operationTime = LocalDateTime.now();
    try {
      persistence.claimCancellation(orderId, userId, operationTime);
      GatewayPayment payment;
      boolean recovered = false;
      try {
        payment =
            gateway.cancelPayment(
                transaction.getPaymentKey(), request.reason(), request.cancelRequestId());
      } catch (PaymentProviderException exception) {
        if (!exception.ambiguous()) {
          recordFailureSignal(userId);
          persistence.markFailed(orderId);
          persistence.completeAttempt(
              attempt.getId(), "FAILED", null, ErrorCode.PAYMENT_PROVIDER_ERROR);
          meters.counter("payment.cancel.failed").increment();
          throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        payment = lookupOrRecovery(orderId, transaction.getPaymentKey());
        recovered = true;
      }
      PaymentPersistenceService.FinalizedCancellation finalized =
          persistence.finalizeCancellation(
              orderId,
              request.cancelRequestId(),
              request.reason(),
              payment,
              operationTime,
              recovered);
      persistence.completeAttempt(attempt.getId(), "SUCCEEDED", Map.of("orderId", orderId), null);
      meters.counter("payment.cancel.success").increment();
      return cancelResult(
          finalized.order(),
          subscriptions.getCurrent(userId),
          finalized.removedUnusedSeconds(),
          finalized.usedUntil(),
          false);
    } finally {
      locks.release(lock);
    }
  }

  @Transactional(readOnly = true)
  public PaymentPageResponse<PaymentHistoryItem> history(Long userId, int page, int size) {
    var result =
        orders.findByUserIdOrderByCreatedAtDesc(
            userId, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50)));
    LocalDateTime now = LocalDateTime.now();
    List<PaymentHistoryItem> items =
        result.getContent().stream()
            .map(
                order -> {
                  PaymentTransaction transaction =
                      transactions.findByPaymentOrderId(order.getId()).orElse(null);
                  SubscriptionService.Contribution contribution =
                      subscriptions.contribution(order, now);
                  boolean cancellable = false;
                  if (order.getStatus() == PaymentOrderStatus.PAID) {
                    try {
                      subscriptions.assertCancellable(order, now);
                      cancellable = true;
                    } catch (BusinessException ignored) {
                      cancellable = false;
                    }
                  }
                  return new PaymentHistoryItem(
                      order.getOrderId(),
                      order.getOrderName(),
                      order.getAmount(),
                      order.getCurrency(),
                      order.getStatus().name(),
                      transaction == null ? null : transaction.getMethod(),
                      transaction == null ? null : transaction.getApprovedAt(),
                      order.getCanceledAt(),
                      transaction == null ? null : transaction.getReceiptUrl(),
                      contribution == null ? null : contribution.periodStart(),
                      contribution == null ? null : contribution.periodEnd(),
                      contribution == null ? 0 : contribution.remainingSeconds(),
                      cancellable);
                })
            .toList();
    return new PaymentPageResponse<>(
        items,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  public PaymentHistoryItem getOrder(Long userId, String orderId) {
    PaymentOrder order = requireOwned(orderId, userId);
    PaymentTransaction transaction = transactions.findByPaymentOrderId(order.getId()).orElse(null);
    SubscriptionService.Contribution contribution =
        subscriptions.contribution(order, LocalDateTime.now());
    boolean cancellable = false;
    try {
      subscriptions.assertCancellable(order, LocalDateTime.now());
      cancellable = order.getStatus() == PaymentOrderStatus.PAID;
    } catch (BusinessException ignored) {
      // Not cancellable.
    }
    return new PaymentHistoryItem(
        order.getOrderId(),
        order.getOrderName(),
        order.getAmount(),
        order.getCurrency(),
        order.getStatus().name(),
        transaction == null ? null : transaction.getMethod(),
        transaction == null ? null : transaction.getApprovedAt(),
        order.getCanceledAt(),
        transaction == null ? null : transaction.getReceiptUrl(),
        contribution == null ? null : contribution.periodStart(),
        contribution == null ? null : contribution.periodEnd(),
        contribution == null ? 0 : contribution.remainingSeconds(),
        cancellable);
  }

  public String reconcile(String orderId) {
    PaymentOrder order = requireOrder(orderId);
    GatewayPayment payment = gateway.getPaymentByOrderId(orderId);
    if (payment.paid()
        && (order.getStatus() == PaymentOrderStatus.RECOVERY_REQUIRED
            || order.getStatus() == PaymentOrderStatus.CONFIRMING)) {
      persistence.finalizePayment(orderId, payment, true);
      return "PAYMENT_RECOVERED";
    }
    if (payment.fullyCanceled()
        && (order.getStatus() == PaymentOrderStatus.RECOVERY_REQUIRED
            || order.getStatus() == PaymentOrderStatus.CANCELING)) {
      PaymentCancellation cancellation =
          cancellations.findByPaymentOrderId(order.getId()).orElse(null);
      UUID key = UUID.nameUUIDFromBytes(("reconcile:" + orderId).getBytes(StandardCharsets.UTF_8));
      persistence.finalizeCancellation(
          orderId, key, "Operational reconciliation", payment, LocalDateTime.now(), true);
      return "CANCELLATION_RECOVERED";
    }
    return "ALREADY_CONSISTENT";
  }

  private GatewayPayment lookupOrRecovery(String orderId, String paymentKey) {
    try {
      GatewayPayment payment = gateway.getPayment(paymentKey);
      if (payment.paid() || payment.fullyCanceled()) return payment;
    } catch (PaymentProviderException ignored) {
      // The recovery scheduler will retry by order id.
    }
    persistence.markRecoveryRequired(orderId);
    throw new BusinessException(ErrorCode.PAYMENT_RECOVERY_REQUIRED);
  }

  private PaymentResultResponse result(
      PaymentOrder order,
      PaymentTransaction transaction,
      SubscriptionResponse subscription,
      boolean replayed,
      boolean recoveryRequired) {
    SubscriptionResponse.EntitlementResponse entitlement =
        subscription.entitlements().stream()
            .filter(
                e ->
                    e.orderId().equals(order.getId().toString())
                        || e.orderId().equals(order.getOrderId()))
            .findFirst()
            .orElse(null);
    return new PaymentResultResponse(
        order.getOrderId(),
        order.getStatus().name(),
        order.getAmount(),
        order.getCurrency(),
        transaction.getPaymentKey(),
        transaction.getMethod(),
        transaction.getApprovedAt(),
        entitlement == null ? null : entitlement.periodStart(),
        entitlement == null ? null : entitlement.periodEnd(),
        subscription.currentPeriodEnd(),
        subscription.currentPlan(),
        replayed,
        recoveryRequired);
  }

  private PaymentCancelResponse cancelResult(
      PaymentOrder order,
      SubscriptionResponse subscription,
      long removed,
      LocalDateTime usedUntil,
      boolean replayed) {
    return new PaymentCancelResponse(
        order.getOrderId(),
        order.getStatus().name(),
        order.getAmount(),
        removed,
        usedUntil,
        subscription.currentPeriodEnd(),
        subscription.currentPlan(),
        replayed);
  }

  private PaymentOrder requireOrder(String orderId) {
    return orders
        .findByOrderId(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
  }

  private PaymentOrder requireOwned(String orderId, Long userId) {
    PaymentOrder order = requireOrder(orderId);
    if (!order.getUserId().equals(userId))
      throw new BusinessException(ErrorCode.PAYMENT_ORDER_FORBIDDEN);
    return order;
  }

  private String fingerprint(String... values) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(String.join("\u001f", values).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private void recordFailureSignal(Long userId) {
    try {
      fsdSignals.recordFailure(userId, Instant.now());
    } catch (RuntimeException ignored) {
      // The durable failed attempt remains available as the Redis fallback.
    }
  }
}
