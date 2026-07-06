package com.finrisk.radar.collector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.collector.client.MarketDataClientException;
import com.finrisk.radar.collector.client.RawMarketData;
import com.finrisk.radar.marketprice.MarketPriceSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDataNormalizerTest {
	private final MarketDataNormalizer normalizer = new MarketDataNormalizer(new ObjectMapper());

	@Test void normalizesCsvWithinRequestedRange() {
		RawMarketData raw = new RawMarketData(MarketPriceSource.CSV, """
				date,open,high,low,close,volume
				2024-01-01,10,12,9,11,100
				2024-01-02,11,13,10,12,200
				""");
		var bars = normalizer.normalize(raw, LocalDate.parse("2024-01-02"), LocalDate.parse("2024-01-31"));
		assertThat(bars).hasSize(1);
		assertThat(bars.get(0).date()).isEqualTo(LocalDate.parse("2024-01-02"));
	}

	@Test void rejectsInvalidCsvHeader() {
		RawMarketData raw = new RawMarketData(MarketPriceSource.CSV, "day,close\n2024-01-01,10");
		assertThatThrownBy(() -> normalizer.normalize(raw, LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-31")))
				.isInstanceOf(MarketDataClientException.class);
	}
}
