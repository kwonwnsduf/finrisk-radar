package com.finrisk.radar.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.auth.dto.AuthResponse;
import com.finrisk.radar.auth.dto.LoginRequest;
import com.finrisk.radar.auth.dto.OAuthCodeExchangeRequest;
import com.finrisk.radar.auth.dto.RefreshRequest;
import com.finrisk.radar.auth.dto.SignupRequest;
import com.finrisk.radar.auth.dto.SignupResponse;
import com.finrisk.radar.auth.dto.TokenResponse;
import com.finrisk.radar.auth.jwt.JwtAuthenticationEntryPoint;
import com.finrisk.radar.auth.jwt.JwtAuthenticationFilter;
import com.finrisk.radar.auth.jwt.JwtAccessDeniedHandler;
import com.finrisk.radar.auth.jwt.JwtProvider;
import com.finrisk.radar.auth.jwt.SecurityErrorResponseWriter;
import com.finrisk.radar.auth.jwt.AccessTokenClaims;
import com.finrisk.radar.auth.oauth.CustomOAuth2UserService;
import com.finrisk.radar.auth.oauth.NoOpOAuth2AuthorizedClientRepository;
import com.finrisk.radar.auth.oauth.OAuthFailureHandler;
import com.finrisk.radar.auth.oauth.OAuthSuccessHandler;
import com.finrisk.radar.global.config.SecurityConfig;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.global.error.GlobalExceptionHandler;
import com.finrisk.radar.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
		SecurityConfig.class,
		JwtAuthenticationFilter.class,
		JwtAuthenticationEntryPoint.class,
		JwtAccessDeniedHandler.class,
		SecurityErrorResponseWriter.class,
		NoOpOAuth2AuthorizedClientRepository.class,
		GlobalExceptionHandler.class
})
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private JwtProvider jwtProvider;

	@MockitoBean
	private TokenRevocationStore tokenRevocationStore;

	@MockitoBean
	private CustomOAuth2UserService customOAuth2UserService;

	@MockitoBean
	private OAuthSuccessHandler oauthSuccessHandler;

	@MockitoBean
	private OAuthFailureHandler oauthFailureHandler;

	@MockitoBean
	private ClientRegistrationRepository clientRegistrationRepository;

	@Test
	void signupIsPublicAndReturnsCreatedResponseWithoutPassword() throws Exception {
		when(authService.signup(any(SignupRequest.class)))
				.thenReturn(new SignupResponse(1L, "user@example.com", "User", "ROLE_USER"));

		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new SignupRequest("user@example.com", "password123", "User"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.id").value(1))
				.andExpect(jsonPath("$.data.email").value("user@example.com"))
				.andExpect(jsonPath("$.data.name").value("User"))
				.andExpect(jsonPath("$.data.role").value("ROLE_USER"))
				.andExpect(jsonPath("$.data.accessToken").doesNotExist())
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andExpect(jsonPath("$.data.password").doesNotExist());
	}

	@Test
	void loginIsPublicAndReturnsSuccessfulResponse() throws Exception {
		when(authService.login(any(LoginRequest.class)))
				.thenReturn(new AuthResponse(
						1L,
						"user@example.com",
						"User",
						"ROLE_USER",
						"access-token",
						"refresh-token"
				));

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new LoginRequest("user@example.com", "password123"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.role").value("ROLE_USER"))
				.andExpect(jsonPath("$.data.accessToken").value("access-token"))
				.andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
	}

	@Test
	void signupValidationReturnsFieldErrors() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new SignupRequest("invalid-email", "short", " "))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("COMMON_001"))
				.andExpect(jsonPath("$.data.email").exists())
				.andExpect(jsonPath("$.data.password").exists())
				.andExpect(jsonPath("$.data.name").exists());
	}

	@Test
	void duplicateSignupUsesCommonErrorResponse() throws Exception {
		when(authService.signup(any(SignupRequest.class)))
				.thenThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new SignupRequest("user@example.com", "password123", "User"))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("AUTH_001"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void malformedJsonReturnsInvalidInputInsteadOfServerError() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{invalid-json}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	@Test
	void refreshIsPublicAndReturnsOnlyNewAccessToken() throws Exception {
		when(authService.refresh(any(RefreshRequest.class)))
				.thenReturn(new TokenResponse("new-access-token"));

		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new RefreshRequest("refresh-token"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist());
	}

	@Test
	void blankRefreshTokenReturnsValidationError() throws Exception {
		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RefreshRequest(" "))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("COMMON_001"))
				.andExpect(jsonPath("$.data.refreshToken").exists());
	}

	@Test
	void oauthCodeExchangeIsPublicAndReturnsTokens() throws Exception {
		when(authService.exchangeOAuthCode(any(OAuthCodeExchangeRequest.class)))
				.thenReturn(new AuthResponse(
						1L, "user@example.com", "User", "ROLE_USER", "access-token", "refresh-token"));

		mockMvc.perform(post("/api/auth/oauth/exchange")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new OAuthCodeExchangeRequest("one-time-code"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.accessToken").value("access-token"))
				.andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
	}

	@Test
	void blankOAuthCodeReturnsValidationError() throws Exception {
		mockMvc.perform(post("/api/auth/oauth/exchange")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new OAuthCodeExchangeRequest(" "))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_001"))
				.andExpect(jsonPath("$.data.code").exists());
	}

	@Test
	void invalidOAuthCodeUsesCommonUnauthorizedResponse() throws Exception {
		when(authService.exchangeOAuthCode(any(OAuthCodeExchangeRequest.class)))
				.thenThrow(new BusinessException(ErrorCode.INVALID_OAUTH_CODE));

		mockMvc.perform(post("/api/auth/oauth/exchange")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new OAuthCodeExchangeRequest("invalid-code"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_006"));
	}

	@Test
	void refreshFailuresUseCommonAuthErrorResponses() throws Exception {
		when(authService.refresh(any(RefreshRequest.class)))
				.thenThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new RefreshRequest("invalid-refresh-token"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_004"));
	}

	@Test
	void redisFailureReturnsServiceUnavailableResponse() throws Exception {
		when(authService.refresh(any(RefreshRequest.class)))
				.thenThrow(new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE));

		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new RefreshRequest("refresh-token"))))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("AUTH_005"));
	}

	@Test
	void authenticatedAccessTokenCanLogoutWithoutRequestBody() throws Exception {
		when(jwtProvider.parseAccessToken("valid-access"))
				.thenReturn(new AccessTokenClaims(
						42L,
						"user@example.com",
						Role.ROLE_USER,
						"access-jti",
						Instant.now().plusSeconds(300)
				));

		mockMvc.perform(post("/api/auth/logout")
						.header("Authorization", "Bearer valid-access"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void logoutRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/auth/logout"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_003"));
	}

	@Test
	void logoutRedisFailureReturnsServiceUnavailable() throws Exception {
		when(jwtProvider.parseAccessToken("valid-access"))
				.thenReturn(new AccessTokenClaims(
						42L,
						"user@example.com",
						Role.ROLE_USER,
						"access-jti",
						Instant.now().plusSeconds(300)
				));
		doThrow(new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE))
				.when(authService).logout(any());

		mockMvc.perform(post("/api/auth/logout")
						.header("Authorization", "Bearer valid-access"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AUTH_005"));
	}
}
