package com.finrisk.radar.backtest.indicator;

import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class RsiCalculator {
	public List<BigDecimal> calculate(List<MarketPriceResponse> prices, int period) {
		List<BigDecimal> rsi = new ArrayList<>(prices.size());
		for (int i = 0; i < prices.size(); i++) rsi.add(null);
		if (prices.size() <= period) return rsi;
		BigDecimal avgGain = BigDecimal.ZERO;
		BigDecimal avgLoss = BigDecimal.ZERO;
		for (int i = 1; i <= period; i++) {
			BigDecimal change = prices.get(i).close().subtract(prices.get(i - 1).close());
			if (change.signum() >= 0) avgGain = avgGain.add(change);
			else avgLoss = avgLoss.add(change.abs());
		}
		avgGain = avgGain.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
		avgLoss = avgLoss.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
		rsi.set(period, value(avgGain, avgLoss));
		for (int i = period + 1; i < prices.size(); i++) {
			BigDecimal change = prices.get(i).close().subtract(prices.get(i - 1).close());
			BigDecimal gain = change.signum() > 0 ? change : BigDecimal.ZERO;
			BigDecimal loss = change.signum() < 0 ? change.abs() : BigDecimal.ZERO;
			avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1)).add(gain)
					.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
			avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1)).add(loss)
					.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
			rsi.set(i, value(avgGain, avgLoss));
		}
		return rsi;
	}

	private BigDecimal value(BigDecimal avgGain, BigDecimal avgLoss) {
		if (avgLoss.signum() == 0) return BigDecimal.valueOf(100);
		BigDecimal rs = avgGain.divide(avgLoss, 10, RoundingMode.HALF_UP);
		return BigDecimal.valueOf(100).subtract(BigDecimal.valueOf(100)
				.divide(BigDecimal.ONE.add(rs), 10, RoundingMode.HALF_UP));
	}
}
