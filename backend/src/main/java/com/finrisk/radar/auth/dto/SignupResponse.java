package com.finrisk.radar.auth.dto;

import com.finrisk.radar.user.User;

public record SignupResponse(
		Long id,
		String email,
		String name,
		String role
) {
	public static SignupResponse from(User user) {
		return new SignupResponse(
				user.getId(),
				user.getEmail(),
				user.getName(),
				user.getRole().name()
		);
	}
}
