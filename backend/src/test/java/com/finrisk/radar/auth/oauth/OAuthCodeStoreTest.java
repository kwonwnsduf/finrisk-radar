package com.finrisk.radar.auth.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthCodeStoreTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private OAuthCodeStore codeStore;

	@BeforeEach
	void setUp() {
		OAuthProperties properties = new OAuthProperties();
		properties.setCodeTtl(Duration.ofSeconds(180));
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		codeStore = new OAuthCodeStore(redisTemplate, properties);
	}

	@Test
	void savesUserIdWithThreeMinuteTtl() {
		codeStore.save("one-time-code", 42L);

		verify(valueOperations).set("oauth:code:one-time-code", "42", Duration.ofSeconds(180));
	}

	@Test
	void consumesCodeAtomicallyWithGetAndDelete() {
		when(valueOperations.getAndDelete("oauth:code:one-time-code")).thenReturn("42");

		assertThat(codeStore.consume("one-time-code")).isEqualTo("42");
		verify(valueOperations).getAndDelete("oauth:code:one-time-code");
	}
}
