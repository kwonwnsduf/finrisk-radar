package com.finrisk.radar.fsd;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.fsd")
public record FsdProperties(
    boolean enabled,
    FailureBurst failureBurst,
    SameIpAccounts sameIpAccounts,
    RapidOrderCreation rapidOrderCreation,
    Metadata metadata,
    FailureSwitchAccount failureSwitchAccount,
    ImmediateCancel immediateCancel,
    CancellationRatio cancellationRatio,
    RepeatedOrderPattern repeatedOrderPattern) {

  @PostConstruct
  void validate() {
    positive(failureBurst.window(), "failure-burst.window");
    positive(failureBurst.redisTtl(), "failure-burst.redis-ttl");
    ordered(failureBurst.reviewCount(), failureBurst.blockCount(), "failure-burst");
    positive(sameIpAccounts.window(), "same-ip-accounts.window");
    positive(sameIpAccounts.redisTtl(), "same-ip-accounts.redis-ttl");
    ordered(sameIpAccounts.reviewCount(), sameIpAccounts.blockCount(), "same-ip-accounts");
    positive(rapidOrderCreation.window(), "rapid-order-creation.window");
    positive(rapidOrderCreation.redisTtl(), "rapid-order-creation.redis-ttl");
    if (rapidOrderCreation.maxOrders() < 1) fail("rapid-order-creation.max-orders");
    positive(failureSwitchAccount.failureWindow(), "failure-switch-account.failure-window");
    positive(failureSwitchAccount.successWindow(), "failure-switch-account.success-window");
    if (failureSwitchAccount.minimumFailures() < 1) fail("failure-switch-account.minimum-failures");
    positive(immediateCancel.immediateWindow(), "immediate-cancel.immediate-window");
    positive(immediateCancel.lookbackWindow(), "immediate-cancel.lookback-window");
    if (immediateCancel.reviewCount() < 1) fail("immediate-cancel.review-count");
    positive(cancellationRatio.lookbackWindow(), "cancellation-ratio.lookback-window");
    if (cancellationRatio.minimumPayments() < 1) fail("cancellation-ratio.minimum-payments");
    ratio(cancellationRatio.reviewRatio(), "cancellation-ratio.review-ratio");
    positive(repeatedOrderPattern.window(), "repeated-order-pattern.window");
    if (repeatedOrderPattern.minimumOrders() < 1) fail("repeated-order-pattern.minimum-orders");
    ratio(repeatedOrderPattern.maximumPaidRatio(), "repeated-order-pattern.maximum-paid-ratio");
    List<RuleControl> controls =
        List.of(
            failureBurst.control(),
            sameIpAccounts.control(),
            rapidOrderCreation.control(),
            metadata.control(),
            failureSwitchAccount.control(),
            immediateCancel.control(),
            cancellationRatio.control(),
            repeatedOrderPattern.control());
    if (controls.stream().anyMatch(control -> control == null || control.priority() < 1)) {
      fail("rule control");
    }
    if (new HashSet<>(controls.stream().map(RuleControl::priority).toList()).size()
        != controls.size()) {
      fail("duplicate rule priority");
    }
    requireMode(failureBurst.control(), FailMode.CLOSED, "failure-burst");
    requireMode(sameIpAccounts.control(), FailMode.CLOSED, "same-ip-accounts");
    requireMode(rapidOrderCreation.control(), FailMode.CLOSED, "rapid-order-creation");
    requireMode(metadata.control(), FailMode.CLOSED, "metadata");
    requireMode(failureSwitchAccount.control(), FailMode.OPEN, "failure-switch-account");
    requireMode(immediateCancel.control(), FailMode.OPEN, "immediate-cancel");
    requireMode(cancellationRatio.control(), FailMode.OPEN, "cancellation-ratio");
    requireMode(repeatedOrderPattern.control(), FailMode.OPEN, "repeated-order-pattern");
  }

  private void ordered(int review, int block, String name) {
    if (review < 1 || block <= review) fail(name + " counts");
  }

  private void positive(Duration value, String name) {
    if (value == null || value.isZero() || value.isNegative()) fail(name);
  }

  private void ratio(double value, String name) {
    if (value < 0 || value > 1) fail(name);
  }

  private void requireMode(RuleControl control, FailMode expected, String name) {
    if (control.failMode() != expected) fail(name + ".fail-mode");
  }

  private void fail(String name) {
    throw new IllegalStateException("Invalid FSD setting: " + name);
  }

  public record RuleControl(boolean enabled, int priority, FailMode failMode) {}

  public enum FailMode {
    OPEN,
    CLOSED
  }

  public record FailureBurst(
      Duration window, int reviewCount, int blockCount, Duration redisTtl, RuleControl control) {}

  public record SameIpAccounts(
      Duration window, int reviewCount, int blockCount, Duration redisTtl, RuleControl control) {}

  public record RapidOrderCreation(
      Duration window, int maxOrders, Duration redisTtl, RuleControl control) {}

  public record Metadata(
      boolean requireRequestId,
      boolean requireUserAgent,
      FsdDecision missingDecision,
      FsdSeverity missingSeverity,
      RuleControl control) {}

  public record FailureSwitchAccount(
      Duration failureWindow, Duration successWindow, int minimumFailures, RuleControl control) {}

  public record ImmediateCancel(
      Duration immediateWindow, Duration lookbackWindow, int reviewCount, RuleControl control) {}

  public record CancellationRatio(
      Duration lookbackWindow, int minimumPayments, double reviewRatio, RuleControl control) {}

  public record RepeatedOrderPattern(
      Duration window, int minimumOrders, double maximumPaidRatio, RuleControl control) {}
}
