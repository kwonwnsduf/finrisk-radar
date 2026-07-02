package com.finrisk.radar.usage;

public record UsageResponse(
		String plan,
		UsageItemResponse backtest,
		UsageItemResponse riskReport,
		UsageItemResponse aiAgent,
		UsageItemResponse watchlist
) {}
