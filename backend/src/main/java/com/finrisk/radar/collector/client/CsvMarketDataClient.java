package com.finrisk.radar.collector.client;

import com.finrisk.radar.marketprice.MarketPriceSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Component
public class CsvMarketDataClient implements MarketDataClient {
	private final ResourceLoader resourceLoader;

	public CsvMarketDataClient(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public RawMarketData fetch(String ticker, LocalDate startDate, LocalDate endDate) {
		if (!ticker.matches("[A-Z0-9._-]+")) {
			throw new MarketDataClientException("CSV ticker contains unsupported characters.");
		}
		Resource resource = resourceLoader.getResource("classpath:market-data-fallback/" + ticker + ".csv");
		if (!resource.exists()) {
			throw new MarketDataClientException("Fallback CSV was not found for " + ticker + ".");
		}
		try (var input = resource.getInputStream()) {
			String payload = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			if (payload.isBlank()) throw new MarketDataClientException("Fallback CSV is empty.");
			return new RawMarketData(MarketPriceSource.CSV, payload);
		} catch (IOException exception) {
			throw new MarketDataClientException("Fallback CSV could not be read.", exception);
		}
	}
}
