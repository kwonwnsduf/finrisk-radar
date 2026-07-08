package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.indicator.DonchianChannelCalculator;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.util.List;

public class DonchianChannelStrategy extends SignalStrategy {
	private final DonchianChannelCalculator calculator = new DonchianChannelCalculator();
	private List<DonchianChannelCalculator.Channel> channels;
	@Override public StrategyType supports() { return StrategyType.DONCHIAN_CHANNEL; }
	@Override protected boolean buySignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return channels.get(index) != null && prices.get(index).close().compareTo(channels.get(index).high()) > 0;
	}
	@Override protected boolean sellSignal(int index, List<MarketPriceResponse> prices) {
		ensure(prices);
		return channels.get(index) != null && prices.get(index).close().compareTo(channels.get(index).low()) < 0;
	}
	private void ensure(List<MarketPriceResponse> prices) {
		if (channels == null || channels.size() != prices.size()) channels = calculator.calculate(prices, 20);
	}
}
