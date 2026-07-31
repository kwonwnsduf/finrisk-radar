package com.finrisk.radar.backtest.event;

import java.time.Instant;
import java.util.UUID;

public record BacktestFailedEvent(UUID jobId, Long userId, Long assetId, Instant failedAt) {}
