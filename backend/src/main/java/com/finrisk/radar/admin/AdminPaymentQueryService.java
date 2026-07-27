package com.finrisk.radar.admin;

import com.finrisk.radar.fsd.*;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.payment.*;
import com.finrisk.radar.user.*;
import jakarta.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPaymentQueryService {
  private final PaymentOrderRepository orders;
  private final PaymentAttemptRepository attempts;
  private final PaymentCancellationRepository cancellations;
  private final FsdRepository fsd;
  private final UserRepository users;

  public AdminPaymentQueryService(
      PaymentOrderRepository orders,
      PaymentAttemptRepository attempts,
      PaymentCancellationRepository cancellations,
      FsdRepository fsd,
      UserRepository users) {
    this.orders = orders;
    this.attempts = attempts;
    this.cancellations = cancellations;
    this.fsd = fsd;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public AdminPage<AdminPaymentItem> list(
      String orderId,
      Long userId,
      String email,
      PaymentOrderStatus status,
      LocalDateTime from,
      LocalDateTime to,
      FsdStatus fsdStatus,
      Boolean recoveryRequired,
      int page,
      int size) {
    Specification<PaymentOrder> specification =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (orderId != null && !orderId.isBlank()) {
            predicates.add(
                cb.like(
                    cb.lower(root.get("orderId")),
                    "%" + orderId.trim().toLowerCase(Locale.ROOT) + "%"));
          }
          if (userId != null) predicates.add(cb.equal(root.get("userId"), userId));
          if (email != null && !email.isBlank()) {
            Subquery<Long> userQuery = query.subquery(Long.class);
            Root<User> user = userQuery.from(User.class);
            userQuery
                .select(user.get("id"))
                .where(
                    cb.like(
                        cb.lower(user.get("email")),
                        "%" + email.trim().toLowerCase(Locale.ROOT) + "%"));
            predicates.add(root.get("userId").in(userQuery));
          }
          PaymentOrderStatus effectiveStatus =
              Boolean.TRUE.equals(recoveryRequired)
                  ? PaymentOrderStatus.RECOVERY_REQUIRED
                  : status;
          if (effectiveStatus != null)
            predicates.add(cb.equal(root.get("status"), effectiveStatus));
          if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
          if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
          if (fsdStatus != null) {
            Subquery<Long> fsdQuery = query.subquery(Long.class);
            Root<FsdEvent> event = fsdQuery.from(FsdEvent.class);
            fsdQuery
                .select(event.get("paymentOrderId"))
                .where(cb.equal(event.get("status"), fsdStatus));
            predicates.add(root.get("id").in(fsdQuery));
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    Page<PaymentOrder> result =
        orders.findAll(
            specification,
            PageRequest.of(
                Math.max(0, page),
                Math.min(100, Math.max(1, size)),
                Sort.by("createdAt").descending()));
    return AdminPage.from(result, enrich(result.getContent()));
  }

  @Transactional(readOnly = true)
  public AdminPaymentDetail get(String orderId) {
    PaymentOrder order =
        orders
            .findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
    List<PaymentAttempt> values =
        attempts.findByPaymentOrderIdOrderByCreatedAtDesc(order.getId());
    AdminPaymentItem item = enrich(List.of(order)).get(0);
    AdminPaymentCancellation cancellation =
        cancellations.findByPaymentOrderId(order.getId()).map(this::cancellation).orElse(null);
    return new AdminPaymentDetail(
        item,
        values.stream().map(this::attempt).toList(),
        cancellation,
        values.stream()
            .filter(value -> "RECOVERY".equals(value.getAttemptType()))
            .findFirst()
            .map(this::attempt)
            .orElse(null));
  }

  private List<AdminPaymentItem> enrich(List<PaymentOrder> values) {
    if (values.isEmpty()) return List.of();
    Set<Long> orderIds = values.stream().map(PaymentOrder::getId).collect(Collectors.toSet());
    Map<Long, User> userById =
        users.findAllById(values.stream().map(PaymentOrder::getUserId).toList()).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    Map<Long, PaymentAttempt> latestFailure = new HashMap<>();
    for (PaymentAttempt attempt :
        attempts.findByPaymentOrderIdInOrderByCreatedAtDesc(orderIds)) {
      if ("FAILED".equals(attempt.getResult())) {
        latestFailure.putIfAbsent(attempt.getPaymentOrderId(), attempt);
      }
    }
    Map<Long, FsdEvent> eventByOrder = new HashMap<>();
    for (FsdEvent event : fsd.findByPaymentOrderIdIn(orderIds)) {
      eventByOrder.merge(
          event.getPaymentOrderId(),
          event,
          (left, right) ->
              left.getDetectedAt().isAfter(right.getDetectedAt()) ? left : right);
    }
    return values.stream()
        .map(
            order -> {
              User user = userById.get(order.getUserId());
              PaymentAttempt failure = latestFailure.get(order.getId());
              FsdEvent event = eventByOrder.get(order.getId());
              return new AdminPaymentItem(
                  order.getOrderId(),
                  order.getUserId(),
                  user == null ? null : user.getEmail(),
                  user == null ? null : user.getName(),
                  order.getProductCode(),
                  order.getOrderName(),
                  order.getAmount(),
                  order.getCurrency(),
                  order.getStatus(),
                  order.getCreatedAt(),
                  order.getPaidAt(),
                  order.getCanceledAt(),
                  failure == null ? null : failure.getErrorCode(),
                  event == null ? null : event.getStatus(),
                  event == null ? null : event.getSeverity(),
                  order.getStatus() == PaymentOrderStatus.RECOVERY_REQUIRED);
            })
        .toList();
  }

  private AdminPaymentAttempt attempt(PaymentAttempt value) {
    return new AdminPaymentAttempt(
        value.getAttemptType(),
        value.getResult(),
        value.getErrorCode(),
        value.getErrorMessage(),
        value.getCreatedAt(),
        value.getCompletedAt());
  }

  private AdminPaymentCancellation cancellation(PaymentCancellation value) {
    return new AdminPaymentCancellation(
        value.getCancelReason(),
        value.getAmount(),
        value.getStatus(),
        value.getRequestedAt(),
        value.getCompletedAt(),
        value.getFailedAt());
  }
}
