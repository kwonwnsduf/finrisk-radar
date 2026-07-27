package com.finrisk.radar.fsd;

import static org.assertj.core.api.Assertions.*;

import com.finrisk.radar.fsd.FsdProperties.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FsdPropertiesTest {
  @Test
  void validSettingsPassStartupValidation() {
    assertThatCode(() -> properties(5, 10, 10, 20).validate()).doesNotThrowAnyException();
  }

  @Test
  void reviewBoundaryMustBeBelowBlockBoundary() {
    assertThatThrownBy(() -> properties(10, 10, 10, 20).validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("failure-burst");
  }

  @Test
  void duplicateRulePriorityFailsStartupValidation() {
    assertThatThrownBy(() -> properties(5, 10, 10, 10).validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("duplicate rule priority");
  }

  private FsdProperties properties(
      int reviewCount, int blockCount, int failurePriority, int ipPriority) {
    return new FsdProperties(
        true,
        new FailureBurst(
            Duration.ofMinutes(10),
            reviewCount,
            blockCount,
            Duration.ofMinutes(10),
            closed(failurePriority)),
        new SameIpAccounts(
            Duration.ofMinutes(30), 3, 5, Duration.ofMinutes(30), closed(ipPriority)),
        new RapidOrderCreation(Duration.ofMinutes(5), 5, Duration.ofMinutes(5), closed(30)),
        new Metadata(false, true, FsdDecision.REVIEW, FsdSeverity.MEDIUM, closed(40)),
        new FailureSwitchAccount(Duration.ofMinutes(10), Duration.ofMinutes(30), 5, open(50)),
        new ImmediateCancel(Duration.ofHours(24), Duration.ofDays(30), 3, open(60)),
        new CancellationRatio(Duration.ofDays(30), 4, 0.5, open(70)),
        new RepeatedOrderPattern(Duration.ofHours(24), 5, 0.4, open(80)));
  }

  private RuleControl closed(int priority) {
    return new RuleControl(true, priority, FailMode.CLOSED);
  }

  private RuleControl open(int priority) {
    return new RuleControl(true, priority, FailMode.OPEN);
  }
}
