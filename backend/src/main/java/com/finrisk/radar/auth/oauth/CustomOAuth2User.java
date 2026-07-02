package com.finrisk.radar.auth.oauth;

import com.finrisk.radar.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User {

	private final Long userId;
	private final Map<String, Object> attributes;
	private final List<GrantedAuthority> authorities;

	private CustomOAuth2User(Long userId, Map<String, Object> attributes, List<GrantedAuthority> authorities) {
		this.userId = userId;
		this.attributes = Map.copyOf(attributes);
		this.authorities = List.copyOf(authorities);
	}

	public static CustomOAuth2User from(User user, Map<String, Object> attributes) {
		return new CustomOAuth2User(
				user.getId(),
				attributes,
				List.of(new SimpleGrantedAuthority(user.getRole().name()))
		);
	}

	public Long getUserId() {
		return userId;
	}

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getName() {
		return userId.toString();
	}
}
