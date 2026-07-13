package com.finrisk.radar.risk.event;

import java.time.Instant;
import java.util.UUID;

public record RiskScoreRequestedEvent(UUID jobId, Long assetId, Long userId, Instant requestedAt) {}
