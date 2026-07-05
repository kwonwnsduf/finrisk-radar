package com.finrisk.radar.asset;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {
	@Mock AssetRepository assetRepository;
	private AssetService service;

	@BeforeEach
	void setUp() {
		service = new AssetService(assetRepository);
	}

	@Test
	void createNormalizesTickerAndMarket() {
		when(assetRepository.existsByTickerAndMarket("005930", "KOSPI")).thenReturn(false);
		when(assetRepository.saveAndFlush(any())).thenAnswer(invocation -> {
			Asset asset = invocation.getArgument(0);
			ReflectionTestUtils.setField(asset, "id", 1L);
			return asset;
		});

		AssetResponse response = service.create(new AssetCreateRequest(
				" 삼성전자 ", " 005930 ", " kospi ", "Semiconductor", "kr", "krw", AssetType.STOCK));

		assertThat(response.name()).isEqualTo("삼성전자");
		assertThat(response.ticker()).isEqualTo("005930");
		assertThat(response.market()).isEqualTo("KOSPI");
		assertThat(response.country()).isEqualTo("KR");
	}

	@Test
	void duplicateTickerAndMarketIsConflict() {
		when(assetRepository.existsByTickerAndMarket("005930", "KOSPI")).thenReturn(true);

		assertThatThrownBy(() -> service.create(new AssetCreateRequest(
				"Samsung", "005930", "KOSPI", null, null, null, AssetType.STOCK)))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.ASSET_ALREADY_EXISTS);
	}

	@Test
	void searchUsesTrimmedKeywordAndType() {
		Asset asset = Asset.create("Samsung", "005930", "KOSPI", null, null, null, AssetType.STOCK);
		when(assetRepository.search("Samsung", AssetType.STOCK)).thenReturn(List.of(asset));

		assertThat(service.search(" Samsung ", AssetType.STOCK)).hasSize(1);
	}

	@Test
	void blankSearchIsInvalidInput() {
		assertThatThrownBy(() -> service.search("  ", null))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	void missingAssetIsNotFound() {
		when(assetRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(99L))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.ASSET_NOT_FOUND);
	}
}
