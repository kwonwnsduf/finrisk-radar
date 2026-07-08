package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.indicator.MacdCalculator;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.util.List;

public class MacdStrategy extends SignalStrategy {
	private final MacdCalculator calculator = new MacdCalculator();
	private List<MacdCalculator.Macd> macd;
	@Override public StrategyType supports() { return StrategyType.MACD; }
	@Override protected boolean buySignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return crossed(index, true);
	}
	@Override protected boolean sellSignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return crossed(index, false);
	}
	private boolean crossed(int index, boolean up) {
		if (index == 0 || macd.get(index) == null || macd.get(index - 1) == null) return false;
		BigDecimal prev = macd.get(index - 1).macd().subtract(macd.get(index - 1).signal());
		BigDecimal now = macd.get(index).macd().subtract(macd.get(index).signal());
		return up ? prev.signum() <= 0 && now.signum() > 0 : prev.signum() >= 0 && now.signum() < 0;
	}
	private void ensure(List<MarketPriceResponse> prices) {
		if (macd == null || macd.size() != prices.size()) macd = calculator.calculate(prices, 12, 26, 9);
	}
}
