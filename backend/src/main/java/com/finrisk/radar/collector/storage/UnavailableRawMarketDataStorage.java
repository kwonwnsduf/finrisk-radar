package com.finrisk.radar.collector.storage;

import com.finrisk.radar.collector.client.RawMarketData;
import java.time.LocalDate;

public class UnavailableRawMarketDataStorage implements RawMarketDataStorage {
	@Override
	public String store(Long assetId, String ticker, LocalDate startDate, LocalDate endDate, RawMarketData rawData) {
		throw new RawStorageException("AWS S3 configuration is incomplete.");
	}
}
