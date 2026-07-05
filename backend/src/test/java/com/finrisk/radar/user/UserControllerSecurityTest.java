package com.finrisk.radar.user;

import com.finrisk.radar.auth.TokenRevocationStore;
import com.finrisk.radar.auth.jwt.AccessTokenClaims;
import com.finrisk.radar.auth.jwt.JwtAuthenticationEntryPoint;
import com.finrisk.radar.auth.jwt.JwtAuthenticationFilter;
import com.finrisk.radar.auth.jwt.JwtAccessDeniedHandler;
import com.finrisk.radar.auth.jwt.JwtProvider;
import com.finrisk.radar.auth.jwt.SecurityErrorResponseWriter;
import com.finrisk.radar.auth.oauth.CustomOAuth2UserService;
import com.finrisk.radar.auth.oauth.NoOpOAuth2AuthorizedClientRepository;
import com.finrisk.radar.auth.oauth.OAuthFailureHandler;
import com.finrisk.radar.auth.oauth.OAuthSuccessHandler;
import com.finrisk.radar.global.config.SecurityConfig;
import com.finrisk.radar.global.error.GlobalExceptionHandler;
import com.finrisk.radar.usage.UsageQueryService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({
		SecurityConfig.class,
		JwtAuthenticationFilter.class,
		JwtAuthenticationEntryPoint.class,
		JwtAccessDeniedHandler.class,
		SecurityErrorResponseWriter.class,
		NoOpOAuth2AuthorizedClientRepository.class,
		GlobalExceptionHandler.class
})
class UserControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtProvider jwtProvider;

	@MockitoBean
	private TokenRevocationStore tokenRevocationStore;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private UsageQueryService usageQueryService;

	@MockitoBean
	private CustomOAuth2UserService customOAuth2UserService;

	@MockitoBean
	private OAuthSuccessHandler oauthSuccessHandler;

	@MockitoBean
	private OAuthFailureHandler oauthFailureHandler;

	@MockitoBean
	private ClientRegistrationRepository clientRegistrationRepository;

	@Test
	void accessTokenCanReadCurrentUser() throws Exception {
		when(jwtProvider.parseAccessToken("valid-access"))
				.thenReturn(new AccessTokenClaims(
						42L,
						"user@example.com",
						Role.ROLE_USER,
						"access-jti",
						Instant.now().plusSeconds(300)
				));
		when(userService.getMe(42L))
				.thenReturn(new MeResponse(42L, "user@example.com", "User", "ROLE_USER", "FREE"));

		mockMvc.perform(get("/api/users/me")
						.header("Authorization", "Bearer valid-access"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(42))
				.andExpect(jsonPath("$.data.email").value("user@example.com"))
				.andExpect(jsonPath("$.data.name").value("User"))
				.andExpect(jsonPath("$.data.role").value("ROLE_USER"))
				.andExpect(jsonPath("$.data.plan").value("FREE"));
	}

	@Test
	void missingTokenReturnsCommonUnauthorizedResponse() throws Exception {
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("AUTH_003"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@ParameterizedTest
	@ValueSource(strings = {"malformed-token", "expired-token", "refresh-token"})
	void rejectedTokenReturnsSameUnauthorizedResponse(String token) throws Exception {
		when(jwtProvider.parseAccessToken(token)).thenThrow(new JwtException("rejected"));

		mockMvc.perform(get("/api/users/me")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("AUTH_003"));
	}
}
