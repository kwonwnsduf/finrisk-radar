package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.indicator.RsiCalculator;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.util.List;

public class RsiStrategy extends SignalStrategy {
	private final RsiCalculator calculator = new RsiCalculator();
	private List<BigDecimal> rsi;

	@Override public StrategyType supports() { return StrategyType.RSI; }
	@Override protected boolean buySignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return rsi.get(index) != null && rsi.get(index).compareTo(BigDecimal.valueOf(30)) < 0;
	}
	@Override protected boolean sellSignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return rsi.get(index) != null && rsi.get(index).compareTo(BigDecimal.valueOf(70)) > 0;
	}
	private void ensure(List<MarketPriceResponse> prices) {
		if (rsi == null || rsi.size() != prices.size()) rsi = calculator.calculate(prices, 14);
	}
}
