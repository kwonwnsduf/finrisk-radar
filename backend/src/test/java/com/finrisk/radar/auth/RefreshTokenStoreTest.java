package com.finrisk.radar.auth;

import com.finrisk.radar.auth.jwt.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenStoreTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private RefreshTokenStore refreshTokenStore;

	@BeforeEach
	void setUp() {
		JwtProperties properties = new JwtProperties();
		properties.setRefreshTokenExpiration(Duration.ofDays(14));
		refreshTokenStore = new RefreshTokenStore(redisTemplate, properties);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
	}

	@Test
	void savesRefreshTokenWithUserKeyAndConfiguredTtl() {
		refreshTokenStore.save(42L, "refresh-token");

		verify(valueOperations).set(
				"refresh:user:42",
				"refresh-token",
				Duration.ofDays(14)
		);
	}

	@Test
	void readsStoredRefreshToken() {
		when(valueOperations.get("refresh:user:42")).thenReturn("refresh-token");

		assertThat(refreshTokenStore.find(42L)).isEqualTo("refresh-token");
	}

	@Test
	void propagatesRedisFailureForServiceTranslation() {
		doThrow(new DataAccessResourceFailureException("redis unavailable"))
				.when(valueOperations).get("refresh:user:42");

		assertThatThrownBy(() -> refreshTokenStore.find(42L))
				.isInstanceOf(DataAccessResourceFailureException.class);
	}
}
