package com.finrisk.radar.risk.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.finrisk.radar.risk.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskRuleRegistryTest {
  @Test
  void sortsRulesByWidePriorityGaps() {
    RiskRule a = rule(1000, RiskRuleType.MATURITY_CONCENTRATION),
        b = rule(100, RiskRuleType.DEBT_RATIO),
        c = rule(3000, RiskRuleType.CREDIT_EVENT);
    RiskRuleRegistry registry = new RiskRuleRegistry(java.util.List.of(a, c, b));
    assertEquals(
        java.util.List.of(100, 1000, 3000),
        registry.rules().stream().map(RiskRule::priority).toList());
  }

  @Test
  void rejectsDuplicatePriority() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new RiskRuleRegistry(
                java.util.List.of(
                    rule(100, RiskRuleType.DEBT_RATIO),
                    rule(100, RiskRuleType.INTEREST_COVERAGE))));
  }

  @Test
  void rejectsDuplicateType() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new RiskRuleRegistry(
                java.util.List.of(
                    rule(100, RiskRuleType.DEBT_RATIO), rule(200, RiskRuleType.DEBT_RATIO))));
  }

  private RiskRule rule(int priority, RiskRuleType type) {
    return new RiskRule() {
      public int priority() {
        return priority;
      }

      public RiskRuleType supports() {
        return type;
      }

      public boolean required() {
        return true;
      }

      public RiskRuleResult evaluate(RiskEvaluationContext c) {
        return RiskRuleResult.calculated(
            type, RiskCategory.FINANCIAL, 0, 5, "NONE", "ok", Map.of(), null);
      }
    };
  }
}
