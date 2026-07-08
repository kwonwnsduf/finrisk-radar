package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.engine.BacktestContext;
import com.finrisk.radar.backtest.engine.BacktestSimulationResult;
import com.finrisk.radar.backtest.engine.TradingSimulator;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.util.List;

public abstract class SignalStrategy implements BacktestStrategy {
	private final TradingSimulator simulator = new TradingSimulator();

	@Override
	public BacktestSimulationResult simulate(BacktestContext context, List<MarketPriceResponse> prices) {
		return simulator.run(context, prices, this::buySignal, this::sellSignal);
	}

	protected abstract boolean buySignal(int index, List<MarketPriceResponse> prices);
	protected abstract boolean sellSignal(int index, List<MarketPriceResponse> prices);
	protected String buyReason(int index) { return supports().name() + "_BUY"; }
	protected String sellReason(int index) { return supports().name() + "_SELL"; }
}
