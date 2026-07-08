package com.finrisk.radar.backtest.api;

import com.finrisk.radar.backtest.StrategyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BacktestCreateRequest(
		@NotNull @Positive Long assetId,
		@NotNull StrategyType strategyType,
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate,
		@Positive BigDecimal initialCash,
		List<StrategyCondition> buyConditions,
		List<StrategyCondition> sellConditions
) {
	public static final BigDecimal DEFAULT_INITIAL_CASH = new BigDecimal("10000000.000000");

	public BacktestCreateRequest(Long assetId, StrategyType strategyType, LocalDate startDate, LocalDate endDate) {
		this(assetId, strategyType, startDate, endDate, DEFAULT_INITIAL_CASH, List.of(), List.of());
	}

	public BacktestCreateRequest {
		initialCash = initialCash == null ? DEFAULT_INITIAL_CASH : initialCash;
		buyConditions = buyConditions == null ? List.of() : List.copyOf(buyConditions);
		sellConditions = sellConditions == null ? List.of() : List.copyOf(sellConditions);
	}
}
