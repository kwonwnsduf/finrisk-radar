package com.finrisk.radar.admin;

import com.finrisk.radar.fsd.*;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.payment.PaymentOrderRepository;
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

  AdminFsdService(FsdRepository events, PaymentOrderRepository orders) {
    this.events = events;
    this.orders = orders;
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
    return new AdminPage<>(
        result.getContent().stream().map(this::response).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  FsdEventResponse get(Long id) {
    return response(require(id));
  }

  @Transactional
  FsdEventResponse review(Long id, FsdStatus status, String note, Long adminId) {
    if (status == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
    FsdEvent event = require(id);
    try {
      event.review(status, note, adminId);
    } catch (IllegalStateException exception) {
      throw new BusinessException(ErrorCode.FSD_INVALID_STATUS_TRANSITION);
    }
    return response(event);
  }

  private FsdEvent require(Long id) {
    return events
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.FSD_EVENT_NOT_FOUND));
  }

  private FsdEventResponse response(FsdEvent event) {
    String orderId =
        event.getPaymentOrderId() == null
            ? null
            : orders
                .findById(event.getPaymentOrderId())
                .map(order -> order.getOrderId())
                .orElse(event.getPaymentOrderId().toString());
    return FsdEventResponse.from(event, orderId);
  }
}
