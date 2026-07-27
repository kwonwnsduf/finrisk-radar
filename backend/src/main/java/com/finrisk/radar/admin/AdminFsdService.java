package com.finrisk.radar.admin;

import com.finrisk.radar.fsd.*;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.payment.PaymentOrderRepository;
import com.finrisk.radar.payment.*;
import com.finrisk.radar.user.*;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AdminFsdService {
  private final FsdRepository events;
  private final PaymentOrderRepository orders;
  private final PaymentAttemptRepository attempts;
  private final UserRepository users;

  AdminFsdService(
      FsdRepository events,
      PaymentOrderRepository orders,
      PaymentAttemptRepository attempts,
      UserRepository users) {
    this.events = events;
    this.orders = orders;
    this.attempts = attempts;
    this.users = users;
  }

  @Transactional(readOnly = true)
  AdminPage<FsdEventResponse> list(
      FsdStatus status,
      FsdSeverity severity,
      FsdDecision decision,
      String ruleCode,
      String search,
      LocalDateTime from,
      LocalDateTime to,
      int page,
      int size) {
    Specification<FsdEvent> specification =
        (root, query, cb) -> {
          List<Predicate> values = new ArrayList<>();
          if (status != null) values.add(cb.equal(root.get("status"), status));
          if (severity != null) values.add(cb.equal(root.get("severity"), severity));
          if (decision != null) values.add(cb.equal(root.get("decision"), decision));
          if (ruleCode != null && !ruleCode.isBlank()) {
            values.add(cb.equal(root.get("ruleCode"), ruleCode.trim()));
          }
          if (from != null) values.add(cb.greaterThanOrEqualTo(root.get("detectedAt"), from));
          if (to != null) values.add(cb.lessThanOrEqualTo(root.get("detectedAt"), to));
          if (search != null && !search.isBlank()) {
            try {
              long id = Long.parseLong(search.trim());
              values.add(
                  cb.or(
                      cb.equal(root.get("userId"), id), cb.equal(root.get("paymentOrderId"), id)));
            } catch (NumberFormatException ignored) {
              orders
                  .findByOrderId(search.trim())
                  .ifPresentOrElse(
                      order -> values.add(cb.equal(root.get("paymentOrderId"), order.getId())),
                      () -> values.add(cb.disjunction()));
            }
          }
          return cb.and(values.toArray(Predicate[]::new));
        };
    Page<FsdEvent> result =
        events.findAll(
            specification,
            PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "detectedAt")));
    return AdminPage.from(result, responses(result.getContent(), false));
  }

  @Transactional(readOnly = true)
  FsdEventResponse get(Long id) {
    return responses(List.of(require(id)), true).get(0);
  }

  @Transactional
  FsdEventResponse review(Long id, FsdStatus status, String note, Long adminId) {
    if (status == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
    FsdEvent event =
        events
            .findByIdForUpdate(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.FSD_EVENT_NOT_FOUND));
    try {
      event.review(status, note, adminId);
    } catch (IllegalStateException exception) {
      throw new BusinessException(ErrorCode.FSD_INVALID_STATUS_TRANSITION);
    }
    return responses(List.of(event), true).get(0);
  }

  private FsdEvent require(Long id) {
    return events
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.FSD_EVENT_NOT_FOUND));
  }

  private List<FsdEventResponse> responses(List<FsdEvent> values, boolean includeAttempts) {
    Map<Long, PaymentOrder> orderById =
        orders
            .findByIdIn(
                values.stream()
                    .map(FsdEvent::getPaymentOrderId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList())
            .stream()
            .collect(java.util.stream.Collectors.toMap(PaymentOrder::getId, v -> v));
    Map<Long, User> userById =
        users.findAllById(values.stream().map(FsdEvent::getUserId).distinct().toList()).stream()
            .collect(java.util.stream.Collectors.toMap(User::getId, v -> v));
    Map<Long, List<AdminPaymentAttempt>> attemptsByOrder = new HashMap<>();
    if (includeAttempts) {
      for (PaymentAttempt attempt :
          attempts.findByPaymentOrderIdInOrderByCreatedAtDesc(orderById.keySet())) {
        attemptsByOrder
            .computeIfAbsent(attempt.getPaymentOrderId(), ignored -> new ArrayList<>())
            .add(
                new AdminPaymentAttempt(
                    attempt.getAttemptType(),
                    attempt.getResult(),
                    attempt.getErrorCode(),
                    attempt.getErrorMessage(),
                    attempt.getCreatedAt(),
                    attempt.getCompletedAt()));
      }
    }
    return values.stream()
        .map(
            event -> {
              PaymentOrder order = orderById.get(event.getPaymentOrderId());
              User user = userById.get(event.getUserId());
              return FsdEventResponse.from(
                  event,
                  order == null ? null : order.getOrderId(),
                  user == null ? null : user.getEmail(),
                  user == null ? null : user.getName(),
                  order == null ? null : order.getAmount(),
                  order == null ? null : order.getCurrency(),
                  order == null ? null : order.getStatus(),
                  order == null
                      ? List.of()
                      : attemptsByOrder.getOrDefault(order.getId(), List.of()));
            })
        .toList();
  }
}
