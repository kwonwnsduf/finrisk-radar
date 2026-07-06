package com.finrisk.radar.collector.event;

import com.finrisk.radar.marketprice.MarketPriceSource;
import java.time.Instant;
import java.util.UUID;

public record MarketDataFetchedEvent(UUID jobId, Long assetId, String ticker, MarketPriceSource source,
		int recordCount, String rawS3Path, Instant completedAt) {}
