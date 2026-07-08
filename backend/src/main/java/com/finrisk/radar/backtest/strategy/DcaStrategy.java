package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.engine.BacktestContext;
import com.finrisk.radar.backtest.engine.BacktestSimulationResult;
import com.finrisk.radar.backtest.engine.TradingSimulator;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.util.List;

public class DcaStrategy implements BacktestStrategy {
	private final TradingSimulator simulator = new TradingSimulator();
	@Override public StrategyType supports() { return StrategyType.DCA; }
	@Override public BacktestSimulationResult simulate(BacktestContext context, List<MarketPriceResponse> prices) {
		return simulator.dca(context, prices, 12);
	}
}
