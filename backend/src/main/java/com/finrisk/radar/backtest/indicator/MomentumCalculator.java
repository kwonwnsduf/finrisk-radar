package com.finrisk.radar.backtest.indicator;

import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class MomentumCalculator {
	public List<BigDecimal> calculate(List<MarketPriceResponse> prices, int period) {
		List<BigDecimal> momentum = new ArrayList<>(prices.size());
		for (int i = 0; i < prices.size(); i++) {
			if (i < period || prices.get(i - period).close().signum() == 0) {
				momentum.add(null);
			} else {
				momentum.add(prices.get(i).close().divide(prices.get(i - period).close(), 10, RoundingMode.HALF_UP)
						.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)));
			}
		}
		return momentum;
	}
}
