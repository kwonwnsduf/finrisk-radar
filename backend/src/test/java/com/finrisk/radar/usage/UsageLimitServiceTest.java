package com.finrisk.radar.usage;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class UsageLimitServiceTest {
	private final UsageLimitService service = new UsageLimitService(mock(StringRedisTemplate.class));

	@Test
	void keyUsesSeoulCalendarMonth() {
		Instant justBeforeSeoulAugust = Instant.parse("2026-07-31T14:59:59Z");
		Instant seoulAugust = Instant.parse("2026-07-31T15:00:00Z");

		assertThat(service.key(42L, UsageType.BACKTEST, justBeforeSeoulAugust))
				.isEqualTo("usage:42:BACKTEST:202607");
		assertThat(service.key(42L, UsageType.BACKTEST, seoulAugust))
				.isEqualTo("usage:42:BACKTEST:202608");
	}

	@Test
	void ttlKeepsOneDayCleanupMargin() {
		Instant instant = Instant.parse("2026-07-15T00:00:00Z");
		assertThat(service.ttl(instant)).isPositive();
		assertThat(instant.plus(service.ttl(instant)))
				.isEqualTo(Instant.parse("2026-08-01T15:00:00Z"));
	}

	@Test
	void redisFailureUsesServiceUnavailableError() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(values);
		when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("unavailable"));

		assertThatThrownBy(() -> new UsageLimitService(redisTemplate).getUsage(42L, UsageType.BACKTEST))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.USAGE_SERVICE_UNAVAILABLE);
		assertThat(ErrorCode.USAGE_SERVICE_UNAVAILABLE.getHttpStatus().value()).isEqualTo(503);
	}

	@Test
	void allUsageLimitsUseTooManyRequests() {
		assertThat(UsageType.values())
				.allSatisfy(type -> assertThat(type.getLimitExceededError().getHttpStatus().value()).isEqualTo(429));
		assertThat(ErrorCode.WATCHLIST_LIMIT_EXCEEDED.getHttpStatus().value()).isEqualTo(429);
	}
}
