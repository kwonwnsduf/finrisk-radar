package com.finrisk.radar.auth.jwt;

public record TokenPair(
		String accessToken,
		String refreshToken
) {
}
