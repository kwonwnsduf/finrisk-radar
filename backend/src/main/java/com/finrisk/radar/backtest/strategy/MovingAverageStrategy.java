package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.indicator.MovingAverageCalculator;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.util.List;

public class MovingAverageStrategy extends SignalStrategy {
	private final MovingAverageCalculator calculator = new MovingAverageCalculator();
	private List<java.math.BigDecimal> ma;

	@Override public StrategyType supports() { return StrategyType.MOVING_AVERAGE; }

	@Override protected boolean buySignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return ma.get(index) != null && prices.get(index).close().compareTo(ma.get(index)) > 0;
	}

	@Override protected boolean sellSignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return ma.get(index) != null && prices.get(index).close().compareTo(ma.get(index)) < 0;
	}

	private void ensure(List<MarketPriceResponse> prices) {
		if (ma == null || ma.size() != prices.size()) ma = calculator.calculate(prices, 20);
	}
}
