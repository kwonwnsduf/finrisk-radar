package com.finrisk.radar.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.collector.client.MarketDataClientException;
import com.finrisk.radar.collector.client.RawMarketData;
import com.finrisk.radar.marketprice.MarketPriceSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class MarketDataNormalizer {
	private final ObjectMapper objectMapper;

	public MarketDataNormalizer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public List<PriceBar> normalize(RawMarketData raw, LocalDate startDate, LocalDate endDate) {
		List<PriceBar> bars = raw.source() == MarketPriceSource.YAHOO
				? normalizeYahoo(raw.payload(), startDate, endDate)
				: normalizeCsv(raw.payload(), startDate, endDate);
		if (bars.isEmpty()) throw new MarketDataClientException("Raw market data contains no valid prices.");
		return bars.stream().sorted(Comparator.comparing(PriceBar::date)).toList();
	}

	private List<PriceBar> normalizeYahoo(String payload, LocalDate startDate, LocalDate endDate) {
		try {
			JsonNode result = objectMapper.readTree(payload).path("chart").path("result").get(0);
			ZoneId zone = ZoneId.of(result.path("meta").path("exchangeTimezoneName").asText("UTC"));
			JsonNode timestamps = result.path("timestamp");
			JsonNode quote = result.path("indicators").path("quote").get(0);
			List<PriceBar> bars = new ArrayList<>();
			for (int i = 0; i < timestamps.size(); i++) {
				LocalDate date = Instant.ofEpochSecond(timestamps.get(i).asLong()).atZone(zone).toLocalDate();
				if (date.isBefore(startDate) || date.isAfter(endDate)) continue;
				BigDecimal open = decimalAt(quote.path("open"), i);
				BigDecimal high = decimalAt(quote.path("high"), i);
				BigDecimal low = decimalAt(quote.path("low"), i);
				BigDecimal close = decimalAt(quote.path("close"), i);
				JsonNode volumeNode = quote.path("volume").path(i);
				if (open == null || high == null || low == null || close == null || volumeNode.isMissingNode()
						|| volumeNode.isNull()) continue;
				PriceBar bar = new PriceBar(date, open, high, low, close, volumeNode.asLong(-1));
				if (valid(bar)) bars.add(bar);
			}
			return bars;
		} catch (MarketDataClientException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new MarketDataClientException("Yahoo raw data could not be normalized.", exception);
		}
	}

	private List<PriceBar> normalizeCsv(String payload, LocalDate startDate, LocalDate endDate) {
		String[] lines = payload.replace("\r", "").split("\n");
		if (lines.length == 0 || !lines[0].replace("\uFEFF", "").equalsIgnoreCase("date,open,high,low,close,volume")) {
			throw new MarketDataClientException("Fallback CSV header is invalid.");
		}
		List<PriceBar> bars = new ArrayList<>();
		try {
			for (int i = 1; i < lines.length; i++) {
				if (lines[i].isBlank()) continue;
				String[] values = lines[i].split(",", -1);
				if (values.length != 6) throw new IllegalArgumentException("Expected six columns.");
				LocalDate date = LocalDate.parse(values[0].trim());
				if (date.isBefore(startDate) || date.isAfter(endDate)) continue;
				PriceBar bar = new PriceBar(date, new BigDecimal(values[1].trim()),
						new BigDecimal(values[2].trim()), new BigDecimal(values[3].trim()),
						new BigDecimal(values[4].trim()), Long.parseLong(values[5].trim()));
				if (valid(bar)) bars.add(bar);
			}
			return bars;
		} catch (RuntimeException exception) {
			throw new MarketDataClientException("Fallback CSV contains invalid price data.", exception);
		}
	}

	private BigDecimal decimalAt(JsonNode array, int index) {
		JsonNode value = array.path(index);
		return value.isMissingNode() || value.isNull() || !value.isNumber() ? null : value.decimalValue();
	}

	private boolean valid(PriceBar bar) {
		if (bar.open().signum() < 0 || bar.high().signum() < 0 || bar.low().signum() < 0
				|| bar.close().signum() < 0 || bar.volume() < 0) return false;
		BigDecimal top = bar.open().max(bar.close());
		BigDecimal bottom = bar.open().min(bar.close());
		return bar.high().compareTo(top) >= 0 && bar.low().compareTo(bottom) <= 0
				&& bar.high().compareTo(bar.low()) >= 0;
	}
}
