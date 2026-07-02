package com.finrisk.radar.auth.jwt;

import com.finrisk.radar.user.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

public record CustomUserPrincipal(
		Long userId,
		String email,
		Role role,
		String tokenId,
		Instant expiresAt
) implements Principal {

	public static CustomUserPrincipal from(AccessTokenClaims claims) {
		return new CustomUserPrincipal(
				claims.userId(),
				claims.email(),
				claims.role(),
				claims.tokenId(),
				claims.expiresAt()
		);
	}

	@Override
	public String getName() {
		return email;
	}

	public List<GrantedAuthority> authorities() {
		return List.of(new SimpleGrantedAuthority(role.name()));
	}
}
