package com.finrisk.radar.risk.event;

import java.time.Instant;
import java.util.UUID;

public record RiskScoreFailedEvent(
    UUID jobId, Long assetId, String failureCode, String failureMessage, Instant failedAt) {}
