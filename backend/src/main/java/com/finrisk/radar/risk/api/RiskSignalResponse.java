package com.finrisk.radar.risk.api;

import com.finrisk.radar.risk.*;
import java.time.LocalDateTime;

public record RiskSignalResponse(
    Long id,
    RiskCategory category,
    String ruleType,
    String signalType,
    RiskSeverity severity,
    int score,
    String message,
    String evidence,
    LocalDateTime detectedAt) {
  public static RiskSignalResponse from(RiskSignal s) {
    return new RiskSignalResponse(
        s.getId(),
        s.getCategory(),
        s.getRuleType(),
        s.getSignalType(),
        s.getSeverity(),
        s.getScore(),
        s.getMessage(),
        s.getEvidence(),
        s.getDetectedAt());
  }
}
