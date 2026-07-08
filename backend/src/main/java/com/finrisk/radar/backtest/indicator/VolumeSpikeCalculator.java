package com.finrisk.radar.backtest.indicator;

import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class VolumeSpikeCalculator {
	public List<BigDecimal> ratios(List<MarketPriceResponse> prices, int period) {
		List<BigDecimal> ratios = new ArrayList<>(prices.size());
		long sum = 0;
		for (int i = 0; i < prices.size(); i++) {
			sum += prices.get(i).volume();
			if (i >= period) sum -= prices.get(i - period).volume();
			if (i + 1 >= period && sum > 0) {
				BigDecimal avg = BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
				ratios.add(BigDecimal.valueOf(prices.get(i).volume()).divide(avg, 10, RoundingMode.HALF_UP));
			} else {
				ratios.add(null);
			}
		}
		return ratios;
	}
}
