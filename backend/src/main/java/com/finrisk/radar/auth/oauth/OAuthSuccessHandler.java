package com.finrisk.radar.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class OAuthSuccessHandler implements AuthenticationSuccessHandler {

	private static final int CODE_BYTES = 32;

	private final OAuthCodeStore codeStore;
	private final OAuthProperties properties;
	private final SecureRandom secureRandom = new SecureRandom();

	public OAuthSuccessHandler(OAuthCodeStore codeStore, OAuthProperties properties) {
		this.codeStore = codeStore;
		this.properties = properties;
	}

	@Override
	public void onAuthenticationSuccess(
			HttpServletRequest request,
			HttpServletResponse response,
			Authentication authentication
	) throws IOException, ServletException {
		if (!(authentication.getPrincipal() instanceof CustomOAuth2User oauthUser)) {
			clearSession(request);
			redirectWithError(response, "authentication_failed");
			return;
		}

		String code = generateCode();
		try {
			codeStore.save(code, oauthUser.getUserId());
		} catch (DataAccessException exception) {
			clearSession(request);
			redirectWithError(response, "service_unavailable");
			return;
		}

		clearSession(request);
		String redirectUri = UriComponentsBuilder.fromUri(properties.getFrontendRedirectUri())
				.queryParam("oauthCode", code)
				.build()
				.encode()
				.toUriString();
		response.sendRedirect(redirectUri);
	}

	private String generateCode() {
		byte[] bytes = new byte[CODE_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private void redirectWithError(HttpServletResponse response, String error) throws IOException {
		String redirectUri = UriComponentsBuilder.fromUri(properties.getFrontendRedirectUri())
				.queryParam("oauthError", error)
				.build()
				.encode()
				.toUriString();
		response.sendRedirect(redirectUri);
	}

	private void clearSession(HttpServletRequest request) {
		SecurityContextHolder.clearContext();
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}
}
