package com.finrisk.radar.risk.kafka;

public final class RiskTopics {
  public static final String REQUESTED = "risk-score-requested",
      CALCULATED = "risk-score-calculated",
      FAILED = "risk-score-failed",
      SIGNAL_DETECTED = "risk-signal-detected";

  private RiskTopics() {}
}
