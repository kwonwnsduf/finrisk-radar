package com.finrisk.radar.marketprice;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.asset.AssetType;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketPriceServiceTest {
	@Mock MarketPriceRepository prices;
	@Mock AssetRepository assets;
	private MarketPriceService service;
	private Asset asset;

	@BeforeEach void setUp() {
		service = new MarketPriceService(prices, assets);
		asset = Asset.create("Samsung", "005930", "KOSPI", null, null, null, AssetType.STOCK);
		ReflectionTestUtils.setField(asset, "id", 1L);
	}

	@Test void prefersManualThenYahooThenCsvForTheSameDate() {
		LocalDate date = LocalDate.parse("2024-01-02");
		when(assets.existsById(1L)).thenReturn(true);
		when(prices.findAllByAsset_IdOrderByDateAsc(1L)).thenReturn(List.of(
				price(date, MarketPriceSource.CSV, "10"),
				price(date, MarketPriceSource.YAHOO, "11"),
				price(date, MarketPriceSource.MANUAL, "12")));

		List<MarketPriceResponse> result = service.getPrices(1L, null, null);
		assertThat(result).singleElement().satisfies(value -> {
			assertThat(value.source()).isEqualTo(MarketPriceSource.MANUAL);
			assertThat(value.close()).isEqualByComparingTo("12");
		});
	}

	@Test void rejectsAnInvertedDateRange() {
		assertThatThrownBy(() -> service.getPrices(1L, LocalDate.parse("2024-02-01"), LocalDate.parse("2024-01-01")))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_DATE_RANGE);
	}

	private MarketPrice price(LocalDate date, MarketPriceSource source, String close) {
		MarketPrice price = new MarketPrice();
		ReflectionTestUtils.setField(price, "asset", asset);
		ReflectionTestUtils.setField(price, "date", date);
		ReflectionTestUtils.setField(price, "open", BigDecimal.TEN);
		ReflectionTestUtils.setField(price, "high", new BigDecimal("13"));
		ReflectionTestUtils.setField(price, "low", new BigDecimal("9"));
		ReflectionTestUtils.setField(price, "close", new BigDecimal(close));
		ReflectionTestUtils.setField(price, "volume", 100L);
		ReflectionTestUtils.setField(price, "source", source);
		return price;
	}
}
