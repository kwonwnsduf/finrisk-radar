package com.finrisk.radar.auth;

import com.finrisk.radar.auth.jwt.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenStore {

	private static final String KEY_PREFIX = "refresh:user:";

	private final StringRedisTemplate redisTemplate;
	private final Duration refreshTokenExpiration;

	public RefreshTokenStore(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
		this.redisTemplate = redisTemplate;
		this.refreshTokenExpiration = jwtProperties.getRefreshTokenExpiration();
	}

	public void save(Long userId, String refreshToken) {
		redisTemplate.opsForValue().set(key(userId), refreshToken, refreshTokenExpiration);
	}

	public String find(Long userId) {
		return redisTemplate.opsForValue().get(key(userId));
	}

	private String key(Long userId) {
		return KEY_PREFIX + userId;
	}
}
