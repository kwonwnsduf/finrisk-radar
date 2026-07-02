package com.finrisk.radar.user;

public record MeResponse(
		Long id,
		String email,
		String name,
		String role,
		String plan
) {
	public static MeResponse from(User user) {
		return new MeResponse(
				user.getId(),
				user.getEmail(),
				user.getName(),
				user.getRole().name(),
				user.getPlan().name()
		);
	}
}
