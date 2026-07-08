package com.finrisk.radar.backtest;

import com.finrisk.radar.backtest.api.StrategyCondition;

import java.util.List;

public record BacktestStrategyConfig(
		List<StrategyCondition> buyConditions,
		List<StrategyCondition> sellConditions
) {
	public static BacktestStrategyConfig empty() {
		return new BacktestStrategyConfig(List.of(), List.of());
	}

	public BacktestStrategyConfig {
		buyConditions = buyConditions == null ? List.of() : List.copyOf(buyConditions);
		sellConditions = sellConditions == null ? List.of() : List.copyOf(sellConditions);
	}
}
