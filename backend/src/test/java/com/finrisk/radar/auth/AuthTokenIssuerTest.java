package com.finrisk.radar.auth;

import com.finrisk.radar.auth.dto.AuthResponse;
import com.finrisk.radar.auth.jwt.JwtProvider;
import com.finrisk.radar.auth.jwt.TokenPair;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenIssuerTest {

	@Mock
	private JwtProvider jwtProvider;

	@Mock
	private RefreshTokenStore refreshTokenStore;

	@InjectMocks
	private AuthTokenIssuer authTokenIssuer;

	@Test
	void issuesBothTokensAndStoresRefreshToken() {
		User user = User.create("user@example.com", "encoded-password", "User");
		ReflectionTestUtils.setField(user, "id", 42L);
		when(jwtProvider.generateTokens(user)).thenReturn(new TokenPair("access-token", "refresh-token"));

		AuthResponse response = authTokenIssuer.issue(user);

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		verify(refreshTokenStore).save(42L, "refresh-token");
	}

	@Test
	void convertsRedisFailureToServiceUnavailable() {
		User user = User.create("user@example.com", "encoded-password", "User");
		ReflectionTestUtils.setField(user, "id", 42L);
		when(jwtProvider.generateTokens(user)).thenReturn(new TokenPair("access-token", "refresh-token"));
		doThrow(new DataAccessResourceFailureException("redis unavailable"))
				.when(refreshTokenStore).save(42L, "refresh-token");

		assertThatThrownBy(() -> authTokenIssuer.issue(user))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).getErrorCode())
				.isEqualTo(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
	}
}
