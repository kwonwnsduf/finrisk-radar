package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.indicator.BollingerBandCalculator;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.util.List;

public class BollingerBandStrategy extends SignalStrategy {
	private final BollingerBandCalculator calculator = new BollingerBandCalculator();
	private List<BollingerBandCalculator.Bands> bands;
	@Override public StrategyType supports() { return StrategyType.BOLLINGER_BAND; }
	@Override protected boolean buySignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return bands.get(index) != null && prices.get(index).close().compareTo(bands.get(index).lower()) <= 0;
	}
	@Override protected boolean sellSignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return bands.get(index) != null && prices.get(index).close().compareTo(bands.get(index).upper()) >= 0;
	}
	private void ensure(List<MarketPriceResponse> prices) {
		if (bands == null || bands.size() != prices.size()) bands = calculator.calculate(prices, 20, BigDecimal.valueOf(2));
	}
}
