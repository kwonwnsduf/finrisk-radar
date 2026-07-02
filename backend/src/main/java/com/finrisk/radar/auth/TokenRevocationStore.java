package com.finrisk.radar.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class TokenRevocationStore {

	private static final String REFRESH_KEY_PREFIX = "refresh:user:";
	private static final String BLACKLIST_KEY_PREFIX = "blacklist:access:";
	private static final String BLACKLIST_VALUE = "1";
	private static final DefaultRedisScript<Long> LOGOUT_SCRIPT = new DefaultRedisScript<>(
			"redis.call('DEL', KEYS[1]); "
					+ "redis.call('PSETEX', KEYS[2], ARGV[2], ARGV[1]); "
					+ "return 1;",
			Long.class
	);

	private final StringRedisTemplate redisTemplate;

	public TokenRevocationStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public void revoke(Long userId, String tokenId, Duration remainingTtl) {
		long ttlMillis = remainingTtl.toMillis();
		if (ttlMillis <= 0) {
			throw new IllegalArgumentException("Blacklist TTL must be positive.");
		}

		redisTemplate.execute(
				LOGOUT_SCRIPT,
				List.of(refreshKey(userId), blacklistKey(tokenId)),
				BLACKLIST_VALUE,
				Long.toString(ttlMillis)
		);
	}

	public boolean isBlacklisted(String tokenId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey(tokenId)));
	}

	private String refreshKey(Long userId) {
		return REFRESH_KEY_PREFIX + userId;
	}

	private String blacklistKey(String tokenId) {
		return BLACKLIST_KEY_PREFIX + tokenId;
	}
}
