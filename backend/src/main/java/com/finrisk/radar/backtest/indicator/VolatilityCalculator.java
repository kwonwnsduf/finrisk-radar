package com.finrisk.radar.backtest.indicator;

import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class VolatilityCalculator {
	public List<BigDecimal> breakoutTargets(List<MarketPriceResponse> prices, BigDecimal k) {
		List<BigDecimal> targets = new ArrayList<>(prices.size());
		targets.add(null);
		for (int i = 1; i < prices.size(); i++) {
			MarketPriceResponse previous = prices.get(i - 1);
			targets.add(prices.get(i).open().add(previous.high().subtract(previous.low()).multiply(k)));
		}
		return targets;
	}
}
