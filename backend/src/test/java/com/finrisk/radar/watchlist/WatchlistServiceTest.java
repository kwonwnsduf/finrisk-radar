package com.finrisk.radar.watchlist;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.asset.AssetType;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.subscription.PlanType;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {
	@Mock WatchlistRepository watchlistRepository;
	@Mock UserRepository userRepository;
	@Mock AssetRepository assetRepository;
	private WatchlistService service;

	@BeforeEach void setUp() { service = new WatchlistService(watchlistRepository, userRepository, assetRepository); }

	@Test
	void freePlanRejectsSixthAsset() {
		User user = User.create("user@example.com", "password", "User");
		Asset asset = asset(1L);
		when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
		when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
		when(watchlistRepository.countByUser_Id(42L)).thenReturn(5L);

		assertThatThrownBy(() -> service.add(42L, new WatchlistCreateRequest(1L)))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.WATCHLIST_LIMIT_EXCEEDED);
		verify(watchlistRepository, never()).saveAndFlush(any());
	}

	@Test
	void premiumPlanDoesNotCountBeforeRegistration() {
		User user = User.create("user@example.com", "password", "User");
		Asset asset = asset(1L);
		ReflectionTestUtils.setField(user, "plan", PlanType.PREMIUM);
		when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
		when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
		when(watchlistRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.add(42L, new WatchlistCreateRequest(1L));

		verify(watchlistRepository, never()).countByUser_Id(42L);
	}

	@Test
	void duplicateAssetIsConflict() {
		User user = User.create("user@example.com", "password", "User");
		Asset asset = asset(1L);
		when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
		when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
		when(watchlistRepository.existsByUser_IdAndAsset_Id(42L, 1L)).thenReturn(true);

		assertThatThrownBy(() -> service.add(42L, new WatchlistCreateRequest(1L)))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.WATCHLIST_ITEM_ALREADY_EXISTS);
	}

	@Test
	void missingAssetIsRejected() {
		User user = User.create("user@example.com", "password", "User");
		when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
		when(assetRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.add(42L, new WatchlistCreateRequest(99L)))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.ASSET_NOT_FOUND);
	}

	private static Asset asset(Long id) {
		Asset asset = Asset.create("Samsung", "005930", "KOSPI", "Semiconductor", "KR", "KRW", AssetType.STOCK);
		ReflectionTestUtils.setField(asset, "id", id);
		return asset;
	}
}
