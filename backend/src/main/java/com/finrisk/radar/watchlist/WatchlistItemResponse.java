package com.finrisk.radar.watchlist;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetType;

import java.time.LocalDateTime;

public record WatchlistItemResponse(
		Long watchlistId,
		Long assetId,
		String name,
		String ticker,
		String market,
		String sector,
		String country,
		String currency,
		AssetType assetType,
		LocalDateTime createdAt
) {
	public static WatchlistItemResponse from(WatchlistItem item) {
		Asset asset = item.getAsset();
		return new WatchlistItemResponse(item.getId(), asset.getId(), asset.getName(), asset.getTicker(),
				asset.getMarket(), asset.getSector(), asset.getCountry(), asset.getCurrency(),
				asset.getAssetType(), item.getCreatedAt());
	}
}
