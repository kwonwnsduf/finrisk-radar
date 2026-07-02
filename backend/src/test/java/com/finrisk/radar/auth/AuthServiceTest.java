package com.finrisk.radar.auth;

import com.finrisk.radar.auth.dto.AuthResponse;
import com.finrisk.radar.auth.dto.LoginRequest;
import com.finrisk.radar.auth.dto.OAuthCodeExchangeRequest;
import com.finrisk.radar.auth.dto.RefreshRequest;
import com.finrisk.radar.auth.dto.SignupRequest;
import com.finrisk.radar.auth.dto.SignupResponse;
import com.finrisk.radar.auth.dto.TokenResponse;
import com.finrisk.radar.auth.jwt.JwtProvider;
import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.auth.jwt.RefreshTokenClaims;
import com.finrisk.radar.auth.oauth.OAuthCodeStore;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.Role;
import com.finrisk.radar.user.AuthProvider;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import io.jsonwebtoken.JwtException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtProvider jwtProvider;

	@Mock
	private RefreshTokenStore refreshTokenStore;

	@Mock
	private TokenRevocationStore tokenRevocationStore;

	@Mock
	private AuthTokenIssuer authTokenIssuer;

	@Mock
	private OAuthCodeStore oauthCodeStore;

	private PasswordEncoder passwordEncoder;
	private AuthService authService;

	@BeforeEach
	void setUp() {
		passwordEncoder = new BCryptPasswordEncoder();
		authService = new AuthService(
				userRepository,
				passwordEncoder,
				jwtProvider,
				refreshTokenStore,
				tokenRevocationStore,
				authTokenIssuer,
				oauthCodeStore
		);
	}

	@Test
	void signupNormalizesInputAndStoresBcryptPasswordWithUserRole() {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		SignupResponse response = authService.signup(
				new SignupRequest("  User@Example.COM ", "password123", "  Fin User  ")
		);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(captor.capture());
		User savedUser = captor.getValue();

		assertThat(savedUser.getEmail()).isEqualTo("user@example.com");
		assertThat(savedUser.getName()).isEqualTo("Fin User");
		assertThat(savedUser.getPassword()).isNotEqualTo("password123");
		assertThat(passwordEncoder.matches("password123", savedUser.getPassword())).isTrue();
		assertThat(savedUser.getRole()).isEqualTo(Role.ROLE_USER);
		assertThat(savedUser.getProvider()).isEqualTo(AuthProvider.LOCAL);
		assertThat(response.role()).isEqualTo("ROLE_USER");
		verifyNoInteractions(jwtProvider, refreshTokenStore, tokenRevocationStore, authTokenIssuer, oauthCodeStore);
	}

	@Test
	void signupRejectsExistingEmail() {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

		assertBusinessError(
				() -> authService.signup(new SignupRequest("user@example.com", "password123", "User")),
				ErrorCode.DUPLICATE_EMAIL
		);
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void signupConvertsDatabaseUniqueConstraintRaceToDuplicateEmail() {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate key"));

		assertBusinessError(
				() -> authService.signup(new SignupRequest("user@example.com", "password123", "User")),
				ErrorCode.DUPLICATE_EMAIL
		);
	}

	@Test
	void loginReturnsUserWhenCredentialsMatch() {
		User user = User.create(
				"user@example.com",
				passwordEncoder.encode("password123"),
				"User"
		);
		ReflectionTestUtils.setField(user, "id", 42L);
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		AuthResponse issued = new AuthResponse(
				42L, "user@example.com", "User", "ROLE_USER", "access-token", "refresh-token");
		when(authTokenIssuer.issue(user)).thenReturn(issued);

		AuthResponse response = authService.login(
				new LoginRequest(" USER@EXAMPLE.COM ", "password123")
		);

		assertThat(response.email()).isEqualTo("user@example.com");
		assertThat(response.name()).isEqualTo("User");
		assertThat(response.role()).isEqualTo("ROLE_USER");
		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		verify(authTokenIssuer).issue(user);
	}

	@Test
	void loginUsesSameErrorForUnknownEmailAndWrongPassword() {
		when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
		assertBusinessError(
				() -> authService.login(new LoginRequest("unknown@example.com", "password123")),
				ErrorCode.INVALID_CREDENTIALS
		);

		User user = User.create(
				"user@example.com",
				passwordEncoder.encode("password123"),
				"User"
		);
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		assertBusinessError(
				() -> authService.login(new LoginRequest("user@example.com", "wrongpass")),
				ErrorCode.INVALID_CREDENTIALS
		);
		verifyNoInteractions(jwtProvider, refreshTokenStore, tokenRevocationStore, authTokenIssuer, oauthCodeStore);
	}

	@Test
	void loginRejectsGoogleAccountEvenWhenPasswordHashMatches() {
		User user = User.createGoogle(
				"user@example.com",
				passwordEncoder.encode("password123"),
				"User",
				"google-subject"
		);
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

		assertBusinessError(
				() -> authService.login(new LoginRequest("user@example.com", "password123")),
				ErrorCode.INVALID_CREDENTIALS
		);
		verifyNoInteractions(authTokenIssuer);
	}

	@Test
	void loginPropagatesTokenIssuerServiceUnavailableError() {
		User user = User.create(
				"user@example.com",
				passwordEncoder.encode("password123"),
				"User"
		);
		ReflectionTestUtils.setField(user, "id", 42L);
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(authTokenIssuer.issue(user))
				.thenThrow(new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE));

		assertBusinessError(
				() -> authService.login(new LoginRequest("user@example.com", "password123")),
				ErrorCode.AUTH_SERVICE_UNAVAILABLE
		);
	}

	@Test
	void exchangeOAuthCodeConsumesCodeAndUsesSharedTokenIssuer() {
		User user = User.createGoogle("user@example.com", "encoded-password", "User", "google-subject");
		ReflectionTestUtils.setField(user, "id", 42L);
		AuthResponse issued = new AuthResponse(
				42L, "user@example.com", "User", "ROLE_USER", "access-token", "refresh-token");
		when(oauthCodeStore.consume("one-time-code")).thenReturn("42");
		when(userRepository.findById(42L)).thenReturn(Optional.of(user));
		when(authTokenIssuer.issue(user)).thenReturn(issued);

		AuthResponse response = authService.exchangeOAuthCode(
				new OAuthCodeExchangeRequest("one-time-code"));

		assertThat(response).isEqualTo(issued);
		verify(oauthCodeStore).consume("one-time-code");
		verify(authTokenIssuer).issue(user);
	}

	@Test
	void exchangeOAuthCodeRejectsMissingMalformedAndDeletedUserCodes() {
		when(oauthCodeStore.consume("missing")).thenReturn(null);
		assertBusinessError(
				() -> authService.exchangeOAuthCode(new OAuthCodeExchangeRequest("missing")),
				ErrorCode.INVALID_OAUTH_CODE);

		when(oauthCodeStore.consume("malformed")).thenReturn("not-a-user-id");
		assertBusinessError(
				() -> authService.exchangeOAuthCode(new OAuthCodeExchangeRequest("malformed")),
				ErrorCode.INVALID_OAUTH_CODE);

		when(oauthCodeStore.consume("deleted-user")).thenReturn("42");
		when(userRepository.findById(42L)).thenReturn(Optional.empty());
		assertBusinessError(
				() -> authService.exchangeOAuthCode(new OAuthCodeExchangeRequest("deleted-user")),
				ErrorCode.INVALID_OAUTH_CODE);
		verifyNoInteractions(authTokenIssuer);
	}

	@Test
	void exchangeOAuthCodeReturnsServiceUnavailableWhenRedisFails() {
		when(oauthCodeStore.consume("one-time-code"))
				.thenThrow(new DataAccessResourceFailureException("redis unavailable"));

		assertBusinessError(
				() -> authService.exchangeOAuthCode(new OAuthCodeExchangeRequest("one-time-code")),
				ErrorCode.AUTH_SERVICE_UNAVAILABLE);
		verifyNoInteractions(authTokenIssuer);
	}

	@Test
	void refreshReturnsNewAccessTokenWithoutRotatingRefreshToken() {
		User user = User.create("user@example.com", "encoded-password", "User");
		ReflectionTestUtils.setField(user, "id", 42L);
		when(jwtProvider.parseRefreshToken("refresh-token"))
				.thenReturn(new RefreshTokenClaims(42L));
		when(refreshTokenStore.find(42L)).thenReturn("refresh-token");
		when(userRepository.findById(42L)).thenReturn(Optional.of(user));
		when(jwtProvider.generateAccessToken(user)).thenReturn("new-access-token");

		TokenResponse response = authService.refresh(new RefreshRequest("refresh-token"));

		assertThat(response.accessToken()).isEqualTo("new-access-token");
		verify(refreshTokenStore).find(42L);
		verify(refreshTokenStore, never()).save(anyLong(), anyString());
	}

	@Test
	void refreshRejectsInvalidJwtBeforeReadingRedis() {
		when(jwtProvider.parseRefreshToken("access-or-invalid-token"))
				.thenThrow(new JwtException("invalid"));

		assertBusinessError(
				() -> authService.refresh(new RefreshRequest("access-or-invalid-token")),
				ErrorCode.INVALID_REFRESH_TOKEN
		);
		verifyNoInteractions(refreshTokenStore);
	}

	@Test
	void refreshRejectsMissingOrMismatchedRedisToken() {
		when(jwtProvider.parseRefreshToken("missing-token"))
				.thenReturn(new RefreshTokenClaims(42L));
		when(refreshTokenStore.find(42L)).thenReturn(null);
		assertBusinessError(
				() -> authService.refresh(new RefreshRequest("missing-token")),
				ErrorCode.INVALID_REFRESH_TOKEN
		);

		when(jwtProvider.parseRefreshToken("mismatched-token"))
				.thenReturn(new RefreshTokenClaims(42L));
		when(refreshTokenStore.find(42L)).thenReturn("stored-token");
		assertBusinessError(
				() -> authService.refresh(new RefreshRequest("mismatched-token")),
				ErrorCode.INVALID_REFRESH_TOKEN
		);
	}

	@Test
	void refreshReturnsServiceUnavailableWhenRedisReadFails() {
		when(jwtProvider.parseRefreshToken("refresh-token"))
				.thenReturn(new RefreshTokenClaims(42L));
		when(refreshTokenStore.find(42L))
				.thenThrow(new DataAccessResourceFailureException("redis unavailable"));

		assertBusinessError(
				() -> authService.refresh(new RefreshRequest("refresh-token")),
				ErrorCode.AUTH_SERVICE_UNAVAILABLE
		);
	}

	@Test
	void refreshRejectsTokenForDeletedUser() {
		when(jwtProvider.parseRefreshToken("refresh-token"))
				.thenReturn(new RefreshTokenClaims(42L));
		when(refreshTokenStore.find(42L)).thenReturn("refresh-token");
		when(userRepository.findById(42L)).thenReturn(Optional.empty());

		assertBusinessError(
				() -> authService.refresh(new RefreshRequest("refresh-token")),
				ErrorCode.INVALID_REFRESH_TOKEN
		);
	}

	@Test
	void logoutRevokesTokensForOnlyTheRemainingAccessLifetime() {
		CustomUserPrincipal principal = new CustomUserPrincipal(
				42L,
				"user@example.com",
				Role.ROLE_USER,
				"access-jti",
				Instant.now().plus(Duration.ofMinutes(5))
		);

		authService.logout(principal);

		ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
		verify(tokenRevocationStore).revoke(eq(42L), eq("access-jti"), ttlCaptor.capture());
		assertThat(ttlCaptor.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(5));
		assertThat(ttlCaptor.getValue()).isGreaterThan(Duration.ofMinutes(4));
	}

	@Test
	void logoutRejectsExpiredPrincipal() {
		CustomUserPrincipal principal = new CustomUserPrincipal(
				42L,
				"user@example.com",
				Role.ROLE_USER,
				"access-jti",
				Instant.now().minusSeconds(1)
		);

		assertBusinessError(() -> authService.logout(principal), ErrorCode.UNAUTHORIZED);
		verifyNoInteractions(tokenRevocationStore);
	}

	@Test
	void logoutReturnsServiceUnavailableWhenRedisFails() {
		CustomUserPrincipal principal = new CustomUserPrincipal(
				42L,
				"user@example.com",
				Role.ROLE_USER,
				"access-jti",
				Instant.now().plus(Duration.ofMinutes(5))
		);
		doThrow(new DataAccessResourceFailureException("redis unavailable"))
				.when(tokenRevocationStore)
				.revoke(anyLong(), anyString(), any(Duration.class));

		assertBusinessError(
				() -> authService.logout(principal),
				ErrorCode.AUTH_SERVICE_UNAVAILABLE
		);
	}

	private void assertBusinessError(Runnable action, ErrorCode errorCode) {
		assertThatThrownBy(action::run)
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).getErrorCode())
				.isEqualTo(errorCode);
	}
}
