package com.finrisk.radar.backtest.engine;

import com.finrisk.radar.backtest.BacktestStrategyConfig;
import com.finrisk.radar.backtest.StrategyType;

import java.math.BigDecimal;

public record BacktestContext(
		StrategyType strategyType,
		BigDecimal initialCash,
		BacktestStrategyConfig strategyConfig,
		BigDecimal feeRate,
		BigDecimal slippageRate
) {
	public static final BigDecimal DEFAULT_INITIAL_CASH = new BigDecimal("10000000.000000");

	public static BacktestContext defaults(StrategyType strategyType) {
		return new BacktestContext(strategyType, DEFAULT_INITIAL_CASH, BacktestStrategyConfig.empty(),
				BigDecimal.ZERO, BigDecimal.ZERO);
	}

	public BacktestContext {
		initialCash = initialCash == null ? DEFAULT_INITIAL_CASH : initialCash;
		strategyConfig = strategyConfig == null ? BacktestStrategyConfig.empty() : strategyConfig;
		feeRate = feeRate == null ? BigDecimal.ZERO : feeRate;
		slippageRate = slippageRate == null ? BigDecimal.ZERO : slippageRate;
	}
}
