package com.finrisk.radar.auth.jwt;

import com.finrisk.radar.auth.TokenRevocationStore;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.Role;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

	@Mock
	private JwtProvider jwtProvider;

	@Mock
	private TokenRevocationStore tokenRevocationStore;

	@Mock
	private SecurityErrorResponseWriter errorResponseWriter;

	private JwtAuthenticationFilter filter;

	@BeforeEach
	void setUp() {
		filter = new JwtAuthenticationFilter(jwtProvider, tokenRevocationStore, errorResponseWriter);
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void registersAuthenticatedPrincipalForValidAccessToken() throws ServletException, IOException {
		when(jwtProvider.parseAccessToken("valid-token"))
				.thenReturn(claims("access-jti"));
		MockHttpServletRequest request = requestWithBearer("valid-token");

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assertThat(authentication).isNotNull();
		assertThat(authentication.isAuthenticated()).isTrue();
		assertThat(authentication.getPrincipal()).isInstanceOf(CustomUserPrincipal.class);
		CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
		assertThat(principal.userId()).isEqualTo(42L);
		assertThat(authentication.getAuthorities())
				.extracting("authority")
				.containsExactly("ROLE_USER");
	}

	@Test
	void leavesContextAnonymousWhenHeaderIsMissing() throws ServletException, IOException {
		filter.doFilter(
				new MockHttpServletRequest(),
				new MockHttpServletResponse(),
				new MockFilterChain()
		);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verifyNoInteractions(jwtProvider, tokenRevocationStore);
	}

	@Test
	void clearsContextWhenTokenIsRejected() throws ServletException, IOException {
		when(jwtProvider.parseAccessToken("rejected-token"))
				.thenThrow(new MalformedJwtException("rejected"));

		filter.doFilter(
				requestWithBearer("rejected-token"),
				new MockHttpServletResponse(),
				new MockFilterChain()
		);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verifyNoInteractions(tokenRevocationStore);
	}

	@Test
	void blacklistedTokenIsNotAuthenticated() throws ServletException, IOException {
		when(jwtProvider.parseAccessToken("blacklisted-token"))
				.thenReturn(claims("blacklisted-jti"));
		when(tokenRevocationStore.isBlacklisted("blacklisted-jti")).thenReturn(true);

		filter.doFilter(
				requestWithBearer("blacklisted-token"),
				new MockHttpServletResponse(),
				new MockFilterChain()
		);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void redisFailureWrites503AndStopsFilterChain() throws ServletException, IOException {
		when(jwtProvider.parseAccessToken("valid-token")).thenReturn(claims("access-jti"));
		when(tokenRevocationStore.isBlacklisted("access-jti"))
				.thenThrow(new DataAccessResourceFailureException("redis unavailable"));
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);

		filter.doFilter(requestWithBearer("valid-token"), response, filterChain);

		verify(errorResponseWriter).write(response, ErrorCode.AUTH_SERVICE_UNAVAILABLE);
		verifyNoInteractions(filterChain);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	private AccessTokenClaims claims(String tokenId) {
		return new AccessTokenClaims(
				42L,
				"user@example.com",
				Role.ROLE_USER,
				tokenId,
				Instant.now().plusSeconds(300)
		);
	}

	private MockHttpServletRequest requestWithBearer(String token) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		return request;
	}
}
