package com.finrisk.radar.collector.kafka;

public final class MarketDataTopics {
	public static final String FETCH_REQUESTED = "market-data-fetch-requested";
	public static final String FETCHED = "market-data-fetched";
	public static final String COLLECTION_FAILED = "collection-failed";
	private MarketDataTopics() {}
}
