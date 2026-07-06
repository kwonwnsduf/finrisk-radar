package com.finrisk.radar.collector.service;

import com.finrisk.radar.marketprice.MarketPriceSource;
import java.util.UUID;

public record CollectionResult(UUID jobId, Long assetId, String ticker, MarketPriceSource source,
		int recordCount, String rawS3Path) {}
