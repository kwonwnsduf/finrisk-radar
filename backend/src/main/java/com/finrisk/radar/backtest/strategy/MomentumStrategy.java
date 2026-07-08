package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.indicator.MomentumCalculator;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.util.List;

public class MomentumStrategy extends SignalStrategy {
	private final MomentumCalculator calculator = new MomentumCalculator();
	private List<BigDecimal> momentum;
	@Override public StrategyType supports() { return StrategyType.MOMENTUM; }
	@Override protected boolean buySignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return momentum.get(index) != null && momentum.get(index).signum() > 0;
	}
	@Override protected boolean sellSignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return momentum.get(index) != null && momentum.get(index).signum() < 0;
	}
	private void ensure(List<MarketPriceResponse> prices) {
		if (momentum == null || momentum.size() != prices.size()) momentum = calculator.calculate(prices, 20);
	}
}
