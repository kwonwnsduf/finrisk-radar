package com.finrisk.radar.risk.api;

import com.finrisk.radar.risk.*;
import java.time.*;
import java.util.UUID;

public record RiskJobResponse(
    UUID jobId,
    Long assetId,
    RiskCalculationStatus status,
    String failureCode,
    String failureMessage,
    String ruleVersion,
    LocalDate dataAsOfDate,
    LocalDateTime requestedAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime failedAt,
    Long riskScoreId) {
  public static RiskJobResponse from(RiskCalculationJob j, Long scoreId) {
    return new RiskJobResponse(
        j.getJobId(),
        j.getAssetId(),
        j.getStatus(),
        j.getFailureCode(),
        j.getFailureMessage(),
        j.getRuleVersion(),
        j.getDataAsOfDate(),
        j.getRequestedAt(),
        j.getStartedAt(),
        j.getCompletedAt(),
        j.getFailedAt(),
        scoreId);
  }
}
