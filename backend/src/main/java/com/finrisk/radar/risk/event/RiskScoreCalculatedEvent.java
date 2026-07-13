package com.finrisk.radar.risk.event;

import com.finrisk.radar.risk.*;
import java.time.Instant;
import java.util.UUID;

public record RiskScoreCalculatedEvent(
    UUID jobId,
    Long assetId,
    Long riskScoreId,
    int totalScore,
    RiskGrade riskGrade,
    DefaultStatus defaultStatus,
    Instant calculatedAt) {}
