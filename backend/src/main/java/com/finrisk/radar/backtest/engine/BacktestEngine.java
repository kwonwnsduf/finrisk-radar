package com.finrisk.radar.backtest.engine;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.util.List;

public interface BacktestEngine {
	BacktestCalculationResult execute(StrategyType strategyType, List<MarketPriceResponse> prices);

	default BacktestCalculationResult execute(BacktestContext context, List<MarketPriceResponse> prices) {
		return execute(context.strategyType(), prices);
	}
}
