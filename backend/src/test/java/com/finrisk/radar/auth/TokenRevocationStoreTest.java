package com.finrisk.radar.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRevocationStoreTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	private TokenRevocationStore tokenRevocationStore;

	@BeforeEach
	void setUp() {
		tokenRevocationStore = new TokenRevocationStore(redisTemplate);
	}

	@Test
	@SuppressWarnings("unchecked")
	void atomicallyDeletesRefreshTokenAndBlacklistsAccessJti() {
		tokenRevocationStore.revoke(42L, "access-jti", Duration.ofMinutes(5));

		ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
		verify(redisTemplate).execute(
				scriptCaptor.capture(),
				eq(List.of("refresh:user:42", "blacklist:access:access-jti")),
				eq("1"),
				eq("300000")
		);
		assertThat(scriptCaptor.getValue().getScriptAsString()).contains("DEL", "PSETEX");
	}

	@Test
	void checksBlacklistByJtiWithoutUsingRawToken() {
		when(redisTemplate.hasKey("blacklist:access:access-jti")).thenReturn(true);

		assertThat(tokenRevocationStore.isBlacklisted("access-jti")).isTrue();
	}

	@Test
	void propagatesRedisFailure() {
		when(redisTemplate.hasKey(any(String.class)))
				.thenThrow(new DataAccessResourceFailureException("redis unavailable"));

		assertThatThrownBy(() -> tokenRevocationStore.isBlacklisted("access-jti"))
				.isInstanceOf(DataAccessResourceFailureException.class);
	}

	@Test
	void rejectsNonPositiveBlacklistTtlBeforeCallingRedis() {
		assertThatThrownBy(() -> tokenRevocationStore.revoke(
				42L,
				"access-jti",
				Duration.ZERO
		)).isInstanceOf(IllegalArgumentException.class);
	}
}
