package com.finrisk.radar.backtest.api;

import com.finrisk.radar.backtest.StrategyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record BacktestCreateRequest(
		@NotNull @Positive Long assetId,
		@NotNull StrategyType strategyType,
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate
) {}
