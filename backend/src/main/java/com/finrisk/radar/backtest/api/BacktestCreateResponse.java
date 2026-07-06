package com.finrisk.radar.backtest.api;

import com.finrisk.radar.backtest.BacktestStatus;

import java.util.UUID;

public record BacktestCreateResponse(UUID jobId, BacktestStatus status) {}
