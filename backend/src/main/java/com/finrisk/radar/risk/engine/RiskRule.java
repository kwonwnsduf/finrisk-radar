package com.finrisk.radar.risk.engine;

public interface RiskRule {
  int priority();

  RiskRuleType supports();

  boolean required();

  RiskRuleResult evaluate(RiskEvaluationContext context);
}
