package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.engine.BacktestContext;
import com.finrisk.radar.backtest.engine.BacktestSimulationResult;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.util.List;

public interface BacktestStrategy {
	StrategyType supports();
	BacktestSimulationResult simulate(BacktestContext context, List<MarketPriceResponse> prices);
}
