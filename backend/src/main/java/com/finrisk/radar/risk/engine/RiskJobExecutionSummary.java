package com.finrisk.radar.risk.engine;

import com.finrisk.radar.risk.CategoryCalculationStatus;

public class RiskJobExecutionSummary {
  private int executed, calculated, unavailable, insufficient, failed;
  private String failedRule;

  public void record(RiskRuleResult r) {
    executed++;
    if (r.status() == CategoryCalculationStatus.CALCULATED) calculated++;
    else if (r.status() == CategoryCalculationStatus.NOT_AVAILABLE) unavailable++;
    else insufficient++;
  }

  public void failed(String rule) {
    executed++;
    failed++;
    failedRule = rule;
  }

  public int executed() {
    return executed;
  }

  public int calculated() {
    return calculated;
  }

  public int unavailable() {
    return unavailable;
  }

  public int insufficient() {
    return insufficient;
  }

  public int failed() {
    return failed;
  }

  public String failedRule() {
    return failedRule;
  }
}
