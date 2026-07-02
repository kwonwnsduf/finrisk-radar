package com.finrisk.radar.auth.jwt;

import com.finrisk.radar.user.Role;

import java.time.Instant;

public record AccessTokenClaims(
		Long userId,
		String email,
		Role role,
		String tokenId,
		Instant expiresAt
) {
}
