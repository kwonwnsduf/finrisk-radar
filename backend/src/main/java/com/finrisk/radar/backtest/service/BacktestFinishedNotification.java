package com.finrisk.radar.backtest.service;

import com.finrisk.radar.backtest.BacktestStatus;
import java.time.Instant;
import java.util.UUID;

public record BacktestFinishedNotification(
    UUID jobId, Long userId, Long assetId, BacktestStatus status, Instant occurredAt) {}
