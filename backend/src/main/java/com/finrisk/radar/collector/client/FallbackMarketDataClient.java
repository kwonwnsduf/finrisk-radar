package com.finrisk.radar.collector.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Primary
@Component
public class FallbackMarketDataClient implements MarketDataClient {
	private static final Logger log = LoggerFactory.getLogger(FallbackMarketDataClient.class);

	private final YahooFinanceMarketDataClient yahooClient;
	private final CsvMarketDataClient csvClient;

	public FallbackMarketDataClient(YahooFinanceMarketDataClient yahooClient, CsvMarketDataClient csvClient) {
		this.yahooClient = yahooClient;
		this.csvClient = csvClient;
	}

	@Override
	public RawMarketData fetch(String ticker, LocalDate startDate, LocalDate endDate) {
		try {
			return yahooClient.fetch(ticker, startDate, endDate);
		} catch (MarketDataClientException exception) {
			log.warn("Yahoo market data unavailable for ticker {}; trying CSV fallback: {}", ticker, exception.getMessage());
			return csvClient.fetch(ticker, startDate, endDate);
		}
	}
}
