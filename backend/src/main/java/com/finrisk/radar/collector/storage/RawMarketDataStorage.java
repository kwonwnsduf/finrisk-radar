package com.finrisk.radar.collector.storage;

import com.finrisk.radar.collector.client.RawMarketData;
import java.time.LocalDate;

public interface RawMarketDataStorage {
	String store(Long assetId, String ticker, LocalDate startDate, LocalDate endDate, RawMarketData rawData);
}
