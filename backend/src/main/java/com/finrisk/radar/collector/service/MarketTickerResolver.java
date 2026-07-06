package com.finrisk.radar.collector.service;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetType;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class MarketTickerResolver {
	public String resolve(Asset asset) {
		if (asset.getAssetType() != AssetType.STOCK && asset.getAssetType() != AssetType.REIT)
			throw new BusinessException(ErrorCode.UNSUPPORTED_MARKET_ASSET);
		String ticker = asset.getTicker().toUpperCase(Locale.ROOT);
		if (ticker.contains(".")) return ticker;
		String market = asset.getMarket() == null ? "" : asset.getMarket().toUpperCase(Locale.ROOT);
		return switch (market) { case "KOSPI" -> ticker + ".KS"; case "KOSDAQ" -> ticker + ".KQ"; default -> ticker; };
	}
	public String validate(Asset asset, String requestedTicker) {
		String expected = resolve(asset);
		if (!expected.equals(requestedTicker.trim().toUpperCase(Locale.ROOT)))
			throw new BusinessException(ErrorCode.TICKER_MISMATCH);
		return expected;
	}
}
