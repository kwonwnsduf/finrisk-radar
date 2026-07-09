package com.finrisk.radar.financial.kafka;

public final class FinancialDataTopics {
	public static final String FETCH_REQUESTED = "financial-data-fetch-requested";
	public static final String FETCHED = "financial-data-fetched";
	public static final String FETCH_FAILED = "financial-data-fetch-failed";
	private FinancialDataTopics() {}
}
