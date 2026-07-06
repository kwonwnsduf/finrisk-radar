package com.finrisk.radar.marketprice;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketPriceResponse(
		Long assetId,
		LocalDate date,
		BigDecimal open,
		BigDecimal high,
		BigDecimal low,
		BigDecimal close,
		Long volume,
		MarketPriceSource source
) {
	static MarketPriceResponse from(MarketPrice price) {
		return new MarketPriceResponse(price.getAsset().getId(), price.getDate(), price.getOpen(),
				price.getHigh(), price.getLow(), price.getClose(), price.getVolume(), price.getSource());
	}
}
