package com.finrisk.radar.fsd;

import com.finrisk.radar.payment.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;

@Service
@EnableConfigurationProperties(FsdProperties.class)
public class FsdEngine {
  private final FsdProperties properties;
  private final FsdRepository events;
  private final MeterRegistry meters;
  private final ApplicationEventPublisher applicationEvents;

  public FsdEngine(
      FsdProperties properties,
      FsdRepository events,
      MeterRegistry meters,
      ApplicationEventPublisher applicationEvents) {
    this.properties = properties;
    this.events = events;
    this.meters = meters;
    this.applicationEvents = applicationEvents;
  }

  @Transactional
  public FsdDecision evaluatePreConfirm(PreConfirmContext context) {
    if (!properties.enabled()) return FsdDecision.ALLOW;
    List<RuleResult> matches = new ArrayList<>();
    if (!context.userId().equals(context.ownerId())) {
      matches.add(block("ORDER_OWNERSHIP_MISMATCH", "Order owner does not match.", "owner"));
    }
    if (context.requestAmount() != context.orderAmount()) {
      matches.add(
          block("AMOUNT_TAMPERING", "Requested amount differs from server order.", "amount"));
    }
    if (context.duplicatePaymentKey()) {
      matches.add(
          block("DUPLICATE_PAYMENT_KEY", "Payment key belongs to another order.", "paymentKey"));
    }
    if (context.abnormalConfirmation()) {
      matches.add(block("DUPLICATE_CONFIRMATION", "Abnormal confirmation retry.", "status"));
    }
    int failures = context.recentFailures();
    if (properties.failureBurst().control().enabled()
        && failures >= properties.failureBurst().blockCount()) {
      matches.add(block("PAYMENT_FAILURE_BURST", "Too many recent payment failures.", "failures"));
    } else if (properties.failureBurst().control().enabled()
        && failures >= properties.failureBurst().reviewCount()) {
      matches.add(
          review(
              "PAYMENT_FAILURE_BURST", FsdSeverity.HIGH, "Repeated payment failures.", failures));
    }
    int ipUsers = context.recentIpUsers();
    if (properties.sameIpAccounts().control().enabled()
        && ipUsers >= properties.sameIpAccounts().blockCount()) {
      matches.add(
          block("SAME_IP_MULTIPLE_ACCOUNT", "Too many accounts share the request IP.", "accounts"));
    } else if (properties.sameIpAccounts().control().enabled()
        && ipUsers >= properties.sameIpAccounts().reviewCount()) {
      matches.add(
          review(
              "SAME_IP_MULTIPLE_ACCOUNT",
              FsdSeverity.HIGH,
              "Several accounts share the request IP.",
              ipUsers));
    }
    if (properties.metadata().control().enabled()
        && ((properties.metadata().requireRequestId() && !context.clientRequestIdPresent())
            || (properties.metadata().requireUserAgent() && !context.userAgentPresent()))) {
      matches.add(
          new RuleResult(
              "SUSPICIOUS_REQUEST_METADATA",
              properties.metadata().missingDecision(),
              properties.metadata().missingSeverity(),
              40,
              "Required request metadata is missing.",
              Map.of(
                  "requestIdPresent",
                  context.clientRequestIdPresent(),
                  "userAgentPresent",
                  context.userAgentPresent())));
    }
    persist(
        context.orderDatabaseId(),
        context.attemptId(),
        context.userId(),
        FsdPhase.PRE_CONFIRM,
        matches);
    return strongest(matches);
  }

  @Transactional
  public void recordRapidOrderBlock(Long userId, Long attemptId, int count) {
    if (!properties.rapidOrderCreation().control().enabled()) return;
    persist(
        null,
        attemptId,
        userId,
        FsdPhase.PRE_CONFIRM,
        List.of(block("RAPID_ORDER_CREATION", "Too many orders were created.", "orders:" + count)));
  }

  @Transactional
  public void evaluatePost(FsdPhase phase, Long orderId, Long userId, Map<String, Object> facts) {
    if (!properties.enabled()) return;
    List<RuleResult> matches = new ArrayList<>();
    if (phase == FsdPhase.POST_PAYMENT
        && properties.repeatedOrderPattern().control().enabled()
        && number(facts, "recentOrders") >= properties.repeatedOrderPattern().minimumOrders()
        && decimal(facts, "paidRatio") <= properties.repeatedOrderPattern().maximumPaidRatio()) {
      matches.add(
          review(
              "REPEATED_ORDER_PATTERN",
              FsdSeverity.HIGH,
              "Repeated unpaid order pattern.",
              number(facts, "recentOrders")));
    }
    if (phase == FsdPhase.POST_PAYMENT
        && properties.failureSwitchAccount().control().enabled()
        && number(facts, "failureSwitchAccounts")
            >= properties.failureSwitchAccount().minimumFailures()) {
      matches.add(
          review(
              "FAILURE_THEN_ACCOUNT_SWITCH",
              FsdSeverity.CRITICAL,
              "A successful payment followed failures by other accounts on the same IP.",
              number(facts, "failureSwitchAccounts")));
    }
    if (properties.sameIpAccounts().control().enabled()
        && number(facts, "sharedIpAccounts") >= properties.sameIpAccounts().reviewCount()) {
      matches.add(
          review(
              phase == FsdPhase.POST_CANCEL
                  ? "SAME_IP_MULTIPLE_ACCOUNT_CANCEL"
                  : "CROSS_ACCOUNT_SHARED_IP",
              FsdSeverity.HIGH,
              "Several accounts share the payment IP.",
              number(facts, "sharedIpAccounts")));
    }
    if (phase == FsdPhase.POST_CANCEL
        && properties.immediateCancel().control().enabled()
        && number(facts, "immediateCancels") >= properties.immediateCancel().reviewCount()) {
      matches.add(
          review(
              "REPEATED_IMMEDIATE_CANCEL",
              FsdSeverity.HIGH,
              "Repeated immediate cancellations.",
              number(facts, "immediateCancels")));
    }
    if (phase == FsdPhase.POST_CANCEL
        && properties.cancellationRatio().control().enabled()
        && number(facts, "payments") >= properties.cancellationRatio().minimumPayments()
        && decimal(facts, "cancelRatio") >= properties.cancellationRatio().reviewRatio()) {
      matches.add(
          review(
              "EXCESSIVE_CANCELLATION_RATIO",
              FsdSeverity.HIGH,
              "Cancellation ratio is unusually high.",
              number(facts, "payments")));
    }
    persist(orderId, null, userId, phase, matches);
  }

  public FsdProperties properties() {
    return properties;
  }

  private void persist(
      Long orderId, Long attemptId, Long userId, FsdPhase phase, List<RuleResult> matches) {
    for (RuleResult result : matches) {
      if (result.decision() == FsdDecision.ALLOW) continue;
      if (orderId != null
          && events.existsByPaymentOrderIdAndRuleCodeAndPhase(orderId, result.ruleCode(), phase))
        continue;
      FsdEvent saved =
          events.saveAndFlush(FsdEvent.detected(orderId, attemptId, userId, phase, result));
      applicationEvents.publishEvent(
          new FsdDetectedNotification(
              saved.getId(),
              saved.getPaymentOrderId(),
              saved.getDecision(),
              saved.getSeverity(),
              Instant.now()));
      meters
          .counter("fsd.detected", "phase", phase.name(), "decision", result.decision().name())
          .increment();
      if (result.decision() == FsdDecision.BLOCK)
        meters.counter("fsd.blocked", "rule", result.ruleCode()).increment();
    }
  }

  private FsdDecision strongest(List<RuleResult> values) {
    return values.stream()
        .map(RuleResult::decision)
        .max(Comparator.comparingInt(this::rank))
        .orElse(FsdDecision.ALLOW);
  }

  private int rank(FsdDecision value) {
    return switch (value) {
      case ALLOW -> 0;
      case REVIEW -> 1;
      case BLOCK -> 2;
    };
  }

  private RuleResult block(String code, String reason, String evidence) {
    return new RuleResult(
        code, FsdDecision.BLOCK, FsdSeverity.CRITICAL, 100, reason, Map.of("signal", evidence));
  }

  private RuleResult review(String code, FsdSeverity severity, String reason, int count) {
    return new RuleResult(code, FsdDecision.REVIEW, severity, 70, reason, Map.of("count", count));
  }

  private int number(Map<String, Object> facts, String key) {
    Object value = facts.get(key);
    return value instanceof Number n ? n.intValue() : 0;
  }

  private double decimal(Map<String, Object> facts, String key) {
    Object value = facts.get(key);
    return value instanceof Number n ? n.doubleValue() : 0;
  }

  public record PreConfirmContext(
      Long orderDatabaseId,
      Long attemptId,
      Long userId,
      Long ownerId,
      long requestAmount,
      long orderAmount,
      boolean duplicatePaymentKey,
      boolean abnormalConfirmation,
      int recentFailures,
      int recentIpUsers,
      boolean clientRequestIdPresent,
      boolean userAgentPresent) {}
}
