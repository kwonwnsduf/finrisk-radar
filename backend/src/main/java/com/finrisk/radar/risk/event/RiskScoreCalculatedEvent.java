package com.finrisk.radar.risk.event;

import com.finrisk.radar.risk.*;
import java.time.Instant;
import java.util.UUID;

public record RiskScoreCalculatedEvent(
    UUID jobId,
    Long userId,
    Long assetId,
    Long riskScoreId,
    int totalScore,
    RiskGrade riskGrade,
    DefaultStatus defaultStatus,
    RiskSeverity highestSeverity,
    long highRiskSignalCount,
    Instant calculatedAt) {}
