package com.finrisk.radar.backtest.indicator;

import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class MovingAverageCalculator {
	public List<BigDecimal> calculate(List<MarketPriceResponse> prices, int period) {
		List<BigDecimal> values = new ArrayList<>(prices.size());
		BigDecimal sum = BigDecimal.ZERO;
		for (int i = 0; i < prices.size(); i++) {
			sum = sum.add(prices.get(i).close());
			if (i >= period) sum = sum.subtract(prices.get(i - period).close());
			values.add(i + 1 >= period ? sum.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP) : null);
		}
		return values;
	}
}
