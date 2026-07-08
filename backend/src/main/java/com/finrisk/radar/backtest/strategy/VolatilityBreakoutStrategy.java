package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.indicator.VolatilityCalculator;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.util.List;

public class VolatilityBreakoutStrategy extends SignalStrategy {
	private final VolatilityCalculator calculator = new VolatilityCalculator();
	private List<BigDecimal> targets;
	@Override public StrategyType supports() { return StrategyType.VOLATILITY_BREAKOUT; }
	@Override protected boolean buySignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return targets.get(index) != null && prices.get(index).high().compareTo(targets.get(index)) >= 0;
	}
	@Override protected boolean sellSignal(int index, List<MarketPriceResponse> prices) {
		return index > 0;
	}
	private void ensure(List<MarketPriceResponse> prices) {
		if (targets == null || targets.size() != prices.size()) targets = calculator.breakoutTargets(prices, BigDecimal.valueOf(0.5));
	}
}
