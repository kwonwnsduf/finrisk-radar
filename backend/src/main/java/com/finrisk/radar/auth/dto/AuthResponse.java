package com.finrisk.radar.auth.dto;

import com.finrisk.radar.user.User;
import com.finrisk.radar.auth.jwt.TokenPair;

public record AuthResponse(
		Long id,
		String email,
		String name,
		String role,
		String accessToken,
		String refreshToken
) {
	public static AuthResponse from(User user, TokenPair tokens) {
		return new AuthResponse(
				user.getId(),
				user.getEmail(),
				user.getName(),
				user.getRole().name(),
				tokens.accessToken(),
				tokens.refreshToken()
		);
	}
}
