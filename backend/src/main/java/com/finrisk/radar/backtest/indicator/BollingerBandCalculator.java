package com.finrisk.radar.backtest.indicator;

import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class BollingerBandCalculator {
	public record Bands(BigDecimal middle, BigDecimal upper, BigDecimal lower) {}

	public List<Bands> calculate(List<MarketPriceResponse> prices, int period, BigDecimal multiplier) {
		List<Bands> bands = new ArrayList<>(prices.size());
		for (int i = 0; i < prices.size(); i++) {
			if (i + 1 < period) {
				bands.add(null);
				continue;
			}
			BigDecimal sum = BigDecimal.ZERO;
			for (int j = i - period + 1; j <= i; j++) sum = sum.add(prices.get(j).close());
			BigDecimal mean = sum.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
			double variance = 0;
			for (int j = i - period + 1; j <= i; j++) {
				double diff = prices.get(j).close().subtract(mean).doubleValue();
				variance += diff * diff;
			}
			BigDecimal sd = BigDecimal.valueOf(Math.sqrt(variance / period));
			BigDecimal width = sd.multiply(multiplier);
			bands.add(new Bands(mean, mean.add(width), mean.subtract(width)));
		}
		return bands;
	}
}
