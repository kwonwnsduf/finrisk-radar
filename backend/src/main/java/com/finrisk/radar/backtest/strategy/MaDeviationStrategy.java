package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.indicator.MovingAverageCalculator;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class MaDeviationStrategy extends SignalStrategy {
	private final MovingAverageCalculator calculator = new MovingAverageCalculator();
	private List<BigDecimal> ma;
	@Override public StrategyType supports() { return StrategyType.MA_DEVIATION; }
	@Override protected boolean buySignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return deviation(index, prices).compareTo(BigDecimal.valueOf(-5)) <= 0;
	}
	@Override protected boolean sellSignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return deviation(index, prices).compareTo(BigDecimal.valueOf(5)) >= 0;
	}
	private BigDecimal deviation(int index, List<MarketPriceResponse> prices) {
		if (ma.get(index) == null || ma.get(index).signum() == 0) return BigDecimal.ZERO;
		return prices.get(index).close().divide(ma.get(index), 10, RoundingMode.HALF_UP)
				.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100));
	}
	private void ensure(List<MarketPriceResponse> prices) {
		if (ma == null || ma.size() != prices.size()) ma = calculator.calculate(prices, 20);
	}
}
