package com.finrisk.radar.auth;

import com.finrisk.radar.auth.dto.AuthResponse;
import com.finrisk.radar.auth.jwt.JwtProvider;
import com.finrisk.radar.auth.jwt.TokenPair;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.User;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class AuthTokenIssuer {

	private final JwtProvider jwtProvider;
	private final RefreshTokenStore refreshTokenStore;

	public AuthTokenIssuer(JwtProvider jwtProvider, RefreshTokenStore refreshTokenStore) {
		this.jwtProvider = jwtProvider;
		this.refreshTokenStore = refreshTokenStore;
	}

	public AuthResponse issue(User user) {
		TokenPair tokens = jwtProvider.generateTokens(user);
		try {
			refreshTokenStore.save(user.getId(), tokens.refreshToken());
		} catch (DataAccessException exception) {
			throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
		}
		return AuthResponse.from(user, tokens);
	}
}
