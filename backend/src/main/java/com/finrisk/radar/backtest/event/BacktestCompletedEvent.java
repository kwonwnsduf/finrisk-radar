package com.finrisk.radar.backtest.event;

import java.time.Instant;
import java.util.UUID;

public record BacktestCompletedEvent(
    UUID jobId, Long userId, Long assetId, Instant completedAt) {}
