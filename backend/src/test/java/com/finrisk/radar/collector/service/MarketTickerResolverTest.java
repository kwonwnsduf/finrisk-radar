package com.finrisk.radar.collector.service;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetType;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketTickerResolverTest {
	private final MarketTickerResolver resolver = new MarketTickerResolver();

	@Test void resolvesKoreanAndUsTickers() {
		assertThat(resolver.resolve(asset("005930", "KOSPI", AssetType.STOCK))).isEqualTo("005930.KS");
		assertThat(resolver.resolve(asset("035720", "KOSDAQ", AssetType.STOCK))).isEqualTo("035720.KQ");
		assertThat(resolver.resolve(asset("AAPL", "NASDAQ", AssetType.STOCK))).isEqualTo("AAPL");
	}

	@Test void rejectsMismatchAndBondIssuer() {
		assertThatThrownBy(() -> resolver.validate(asset("005930", "KOSPI", AssetType.STOCK), "AAPL"))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.TICKER_MISMATCH);
		assertThatThrownBy(() -> resolver.resolve(asset("JTBC", "PRIVATE", AssetType.BOND_ISSUER)))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MARKET_ASSET);
	}

	private Asset asset(String ticker, String market, AssetType type) {
		return Asset.create("Asset", ticker, market, null, null, null, type);
	}
}
