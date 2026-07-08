package com.finrisk.radar.backtest.engine;

import java.util.List;

public record BacktestSimulationResult(
		List<DailyPortfolioValue> dailyPortfolioValues,
		List<Trade> trades
) {
	public BacktestSimulationResult {
		dailyPortfolioValues = dailyPortfolioValues == null ? List.of() : List.copyOf(dailyPortfolioValues);
		trades = trades == null ? List.of() : List.copyOf(trades);
	}
}
