package com.finrisk.radar.backtest.indicator;

import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class MacdCalculator {
	public record Macd(BigDecimal macd, BigDecimal signal, BigDecimal histogram) {}

	public List<Macd> calculate(List<MarketPriceResponse> prices, int shortPeriod, int longPeriod, int signalPeriod) {
		List<BigDecimal> shortEma = ema(prices, shortPeriod);
		List<BigDecimal> longEma = ema(prices, longPeriod);
		List<BigDecimal> macdLine = new ArrayList<>(prices.size());
		for (int i = 0; i < prices.size(); i++) {
			macdLine.add(shortEma.get(i) == null || longEma.get(i) == null ? null : shortEma.get(i).subtract(longEma.get(i)));
		}
		List<BigDecimal> signal = emaValues(macdLine, signalPeriod);
		List<Macd> result = new ArrayList<>(prices.size());
		for (int i = 0; i < prices.size(); i++) {
			if (macdLine.get(i) == null || signal.get(i) == null) result.add(null);
			else result.add(new Macd(macdLine.get(i), signal.get(i), macdLine.get(i).subtract(signal.get(i))));
		}
		return result;
	}

	private List<BigDecimal> ema(List<MarketPriceResponse> prices, int period) {
		List<BigDecimal> closes = prices.stream().map(MarketPriceResponse::close).toList();
		return emaValues(closes, period);
	}

	private List<BigDecimal> emaValues(List<BigDecimal> values, int period) {
		List<BigDecimal> ema = new ArrayList<>(values.size());
		for (int i = 0; i < values.size(); i++) ema.add(null);
		BigDecimal multiplier = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(period + 1L), 10, RoundingMode.HALF_UP);
		BigDecimal previous = null;
		int seen = 0;
		BigDecimal seed = BigDecimal.ZERO;
		for (int i = 0; i < values.size(); i++) {
			BigDecimal value = values.get(i);
			if (value == null) continue;
			seen++;
			if (previous == null) {
				seed = seed.add(value);
				if (seen == period) {
					previous = seed.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
					ema.set(i, previous);
				}
			} else {
				previous = value.subtract(previous).multiply(multiplier).add(previous);
				ema.set(i, previous);
			}
		}
		return ema;
	}
}
