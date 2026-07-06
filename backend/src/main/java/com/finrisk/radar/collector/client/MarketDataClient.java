package com.finrisk.radar.collector.client;

import java.time.LocalDate;

public interface MarketDataClient {
	RawMarketData fetch(String ticker, LocalDate startDate, LocalDate endDate);
}
