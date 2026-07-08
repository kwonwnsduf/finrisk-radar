package com.finrisk.radar.backtest.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BacktestCalculationResult(
		LocalDate firstPriceDate,
		LocalDate lastPriceDate,
		BigDecimal initialClose,
		BigDecimal finalClose,
		BigDecimal totalReturnRate,
		BigDecimal cagr,
		BigDecimal mdd,
		BigDecimal winRate,
		Integer tradeCount,
		BigDecimal sharpeRatio,
		BigDecimal benchmarkReturnRate,
		List<MonthlyReturn> monthlyReturns,
		List<DailyPortfolioValue> dailyPortfolioValues,
		List<Trade> trades
) {
	public BacktestCalculationResult(LocalDate firstPriceDate, LocalDate lastPriceDate,
			BigDecimal initialClose, BigDecimal finalClose, BigDecimal totalReturnRate) {
		this(firstPriceDate, lastPriceDate, initialClose, finalClose, totalReturnRate,
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO,
				totalReturnRate, List.of(), List.of(), List.of());
	}

	public BacktestCalculationResult {
		tradeCount = tradeCount == null ? 0 : tradeCount;
		monthlyReturns = monthlyReturns == null ? List.of() : List.copyOf(monthlyReturns);
		dailyPortfolioValues = dailyPortfolioValues == null ? List.of() : List.copyOf(dailyPortfolioValues);
		trades = trades == null ? List.of() : List.copyOf(trades);
	}
}
