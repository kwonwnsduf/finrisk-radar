package com.finrisk.radar.watchlist;

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
	private WatchlistService service;

	@BeforeEach void setUp() { service = new WatchlistService(watchlistRepository, userRepository); }

	@Test
	void freePlanRejectsSixthAsset() {
		User user = User.create("user@example.com", "password", "User");
		when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
		when(watchlistRepository.countByUserId(42L)).thenReturn(5L);

		assertThatThrownBy(() -> service.add(42L, new WatchlistCreateRequest("AAPL")))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.WATCHLIST_LIMIT_EXCEEDED);
		verify(watchlistRepository, never()).saveAndFlush(any());
	}

	@Test
	void premiumPlanDoesNotCountBeforeRegistration() {
		User user = User.create("user@example.com", "password", "User");
		ReflectionTestUtils.setField(user, "plan", PlanType.PREMIUM);
		when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
		when(watchlistRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.add(42L, new WatchlistCreateRequest("aapl"));

		verify(watchlistRepository, never()).countByUserId(42L);
	}

	@Test
	void duplicateAssetIsConflict() {
		User user = User.create("user@example.com", "password", "User");
		when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
		when(watchlistRepository.existsByUserIdAndAssetCode(42L, "AAPL")).thenReturn(true);

		assertThatThrownBy(() -> service.add(42L, new WatchlistCreateRequest(" aapl ")))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.WATCHLIST_ITEM_ALREADY_EXISTS);
	}
}
