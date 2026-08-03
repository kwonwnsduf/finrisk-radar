package com.finrisk.radar.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuthFailureHandler implements AuthenticationFailureHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuthFailureHandler.class);

	private final OAuthProperties properties;

	public OAuthFailureHandler(OAuthProperties properties) {
		this.properties = properties;
	}

	@Override
	public void onAuthenticationFailure(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException exception
	) throws IOException {
		String errorCode = exception instanceof OAuth2AuthenticationException oauthException
				? oauthException.getError().getErrorCode()
				: exception.getClass().getSimpleName();
		log.warn("OAuth2 login failed: errorCode={}, exceptionType={}",
				errorCode, exception.getClass().getSimpleName());

		SecurityContextHolder.clearContext();
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		String redirectUri = UriComponentsBuilder.fromUri(properties.getFrontendRedirectUri())
				.queryParam("oauthError", "authentication_failed")
				.build()
				.encode()
				.toUriString();
		response.sendRedirect(redirectUri);
	}
}
