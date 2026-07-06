package com.finrisk.radar.collector.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.marketprice.MarketPriceSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.LocalDate;
import java.time.Duration;
import java.time.ZoneOffset;

@Component
public class YahooFinanceMarketDataClient implements MarketDataClient {
	private static final Logger log = LoggerFactory.getLogger(YahooFinanceMarketDataClient.class);
	private static final int MAX_ATTEMPTS = 3;
	private static final long INITIAL_BACKOFF_MILLIS = 250;
	private static final String USER_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
					+ "Chrome/126.0 Safari/537.36 FinRiskRadar/1.0";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	@Autowired
	public YahooFinanceMarketDataClient(RestClient.Builder builder, ObjectMapper objectMapper,
			@Value("${app.market-data.yahoo.base-url:https://query1.finance.yahoo.com}") String baseUrl) {
		this(configuredBuilder(builder, baseUrl).build(), objectMapper);
	}

	YahooFinanceMarketDataClient(RestClient restClient, ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	static RestClient.Builder configuredBuilder(RestClient.Builder builder, String baseUrl) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		return builder.requestFactory(requestFactory)
				.baseUrl(baseUrl)
				.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
	}

	@Override
	public RawMarketData fetch(String ticker, LocalDate startDate, LocalDate endDate) {
		long period1 = startDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
		long period2 = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				String payload = request(ticker, period1, period2);
				if (payload == null || !hasDailyData(payload)) {
					throw new MarketDataClientException("Yahoo Finance returned no daily prices.");
				}
				return new RawMarketData(MarketPriceSource.YAHOO, payload);
			} catch (MarketDataClientException exception) {
				throw exception;
			} catch (RestClientResponseException exception) {
				int status = exception.getStatusCode().value();
				boolean retryable = status == 429 || exception.getStatusCode().is5xxServerError();
				log.warn("Yahoo Finance request failed for ticker {} with HTTP {} (attempt {}/{})",
						ticker, status, attempt, MAX_ATTEMPTS);
				if (!retryable || attempt == MAX_ATTEMPTS) {
					throw new MarketDataClientException("Yahoo Finance request failed with HTTP " + status + ".", exception);
				}
				backoff(attempt);
			} catch (RestClientException exception) {
				log.warn("Yahoo Finance network request failed for ticker {} (attempt {}/{})",
						ticker, attempt, MAX_ATTEMPTS);
				if (attempt == MAX_ATTEMPTS) {
					throw new MarketDataClientException("Yahoo Finance network request failed.", exception);
				}
				backoff(attempt);
		}
		}
		throw new MarketDataClientException("Yahoo Finance request failed.");
	}

	private String request(String ticker, long period1, long period2) {
		return restClient.get()
				.uri(uri -> uri.path("/v8/finance/chart/{ticker}")
						.queryParam("period1", period1)
						.queryParam("period2", period2)
						.queryParam("interval", "1d")
						.queryParam("events", "history")
						.build(ticker))
				.retrieve()
				.body(String.class);
	}

	private void backoff(int attempt) {
		try {
			Thread.sleep(INITIAL_BACKOFF_MILLIS * (1L << (attempt - 1)));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new MarketDataClientException("Yahoo Finance retry was interrupted.", exception);
		}
	}

	private boolean hasDailyData(String payload) {
		try {
			JsonNode chart = objectMapper.readTree(payload).path("chart");
			JsonNode result = chart.path("result");
			if (!chart.path("error").isNull() || !result.isArray() || result.isEmpty()) return false;
			JsonNode first = result.get(0);
			JsonNode timestamps = first.path("timestamp");
			JsonNode quotes = first.path("indicators").path("quote");
			if (!timestamps.isArray() || timestamps.isEmpty() || !quotes.isArray() || quotes.isEmpty()) return false;
			for (JsonNode close : quotes.get(0).path("close")) {
				if (close.isNumber()) return true;
			}
			return false;
		} catch (Exception exception) {
			throw new MarketDataClientException("Yahoo Finance returned malformed JSON.", exception);
		}
	}
}
