package com.finrisk.radar.auth.oauth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OAuthCodeStore {

	private static final String KEY_PREFIX = "oauth:code:";

	private final StringRedisTemplate redisTemplate;
	private final OAuthProperties properties;

	public OAuthCodeStore(StringRedisTemplate redisTemplate, OAuthProperties properties) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
	}

	public void save(String code, Long userId) {
		redisTemplate.opsForValue().set(key(code), userId.toString(), properties.getCodeTtl());
	}

	public String consume(String code) {
		return redisTemplate.opsForValue().getAndDelete(key(code));
	}

	private String key(String code) {
		return KEY_PREFIX + code;
	}
}
