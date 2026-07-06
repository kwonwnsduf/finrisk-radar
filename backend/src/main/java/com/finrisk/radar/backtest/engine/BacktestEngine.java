package com.finrisk.radar.backtest.engine;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.util.List;

public interface BacktestEngine {
	BacktestCalculationResult execute(StrategyType strategyType, List<MarketPriceResponse> prices);
}
