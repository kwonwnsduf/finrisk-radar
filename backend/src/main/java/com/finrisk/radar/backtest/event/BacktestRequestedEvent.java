package com.finrisk.radar.backtest.event;

import java.time.Instant;
import java.util.UUID;

public record BacktestRequestedEvent(UUID jobId, Instant requestedAt) {}
