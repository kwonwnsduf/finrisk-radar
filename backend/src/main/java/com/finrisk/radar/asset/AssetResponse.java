package com.finrisk.radar.asset;

public record AssetResponse(
		Long id,
		String name,
		String ticker,
		String market,
		String sector,
		String country,
		String currency,
		AssetType assetType
) {
	public static AssetResponse from(Asset asset) {
		return new AssetResponse(asset.getId(), asset.getName(), asset.getTicker(), asset.getMarket(),
				asset.getSector(), asset.getCountry(), asset.getCurrency(), asset.getAssetType());
	}
}
