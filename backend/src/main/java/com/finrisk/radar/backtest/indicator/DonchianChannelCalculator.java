package com.finrisk.radar.backtest.indicator;

import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DonchianChannelCalculator {
	public record Channel(BigDecimal high, BigDecimal low) {}

	public List<Channel> calculate(List<MarketPriceResponse> prices, int period) {
		List<Channel> channels = new ArrayList<>(prices.size());
		for (int i = 0; i < prices.size(); i++) {
			if (i < period) {
				channels.add(null);
				continue;
			}
			BigDecimal high = prices.get(i - period).high();
			BigDecimal low = prices.get(i - period).low();
			for (int j = i - period; j < i; j++) {
				if (prices.get(j).high().compareTo(high) > 0) high = prices.get(j).high();
				if (prices.get(j).low().compareTo(low) < 0) low = prices.get(j).low();
			}
			channels.add(new Channel(high, low));
		}
		return channels;
	}
}
