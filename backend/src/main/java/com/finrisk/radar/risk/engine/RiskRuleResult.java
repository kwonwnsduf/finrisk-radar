package com.finrisk.radar.risk.engine;

import com.finrisk.radar.risk.*;
import java.util.Map;

public record RiskRuleResult(
    RiskRuleType ruleType,
    RiskCategory category,
    CategoryCalculationStatus status,
    int score,
    int maxScore,
    RiskSeverity severity,
    String signalType,
    String message,
    Map<String, Object> evidence,
    Long sourceId) {
  public static RiskRuleResult unavailable(
      RiskRuleType t, RiskCategory c, int max, String message) {
    return new RiskRuleResult(
        t,
        c,
        CategoryCalculationStatus.NOT_AVAILABLE,
        0,
        max,
        RiskSeverity.INFO,
        t.name(),
        message,
        Map.of("reason", message),
        null);
  }

  public static RiskRuleResult calculated(
      RiskRuleType t,
      RiskCategory c,
      int score,
      int max,
      String signal,
      String message,
      Map<String, Object> evidence,
      Long source) {
    return new RiskRuleResult(
        t,
        c,
        CategoryCalculationStatus.CALCULATED,
        score,
        max,
        severity(score, max),
        signal,
        message,
        evidence,
        source);
  }

  private static RiskSeverity severity(int score, int max) {
    if (score <= 0) return RiskSeverity.INFO;
    double r = (double) score / max;
    if (r >= .8) return RiskSeverity.CRITICAL;
    if (r >= .6) return RiskSeverity.HIGH;
    if (r >= .3) return RiskSeverity.MEDIUM;
    return RiskSeverity.LOW;
  }
}
