package com.finrisk.radar.auth;

import com.finrisk.radar.auth.dto.AuthResponse;
import com.finrisk.radar.auth.dto.LoginRequest;
import com.finrisk.radar.auth.dto.OAuthCodeExchangeRequest;
import com.finrisk.radar.auth.dto.RefreshRequest;
import com.finrisk.radar.auth.dto.SignupRequest;
import com.finrisk.radar.auth.dto.SignupResponse;
import com.finrisk.radar.auth.dto.TokenResponse;
import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.auth.jwt.JwtProvider;
import com.finrisk.radar.auth.jwt.RefreshTokenClaims;
import com.finrisk.radar.auth.oauth.OAuthCodeStore;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.AuthProvider;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import org.springframework.dao.DataAccessException;
import io.jsonwebtoken.JwtException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final RefreshTokenStore refreshTokenStore;
	private final TokenRevocationStore tokenRevocationStore;
	private final AuthTokenIssuer authTokenIssuer;
	private final OAuthCodeStore oauthCodeStore;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtProvider jwtProvider,
			RefreshTokenStore refreshTokenStore,
			TokenRevocationStore tokenRevocationStore,
			AuthTokenIssuer authTokenIssuer,
			OAuthCodeStore oauthCodeStore
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtProvider = jwtProvider;
		this.refreshTokenStore = refreshTokenStore;
		this.tokenRevocationStore = tokenRevocationStore;
		this.authTokenIssuer = authTokenIssuer;
		this.oauthCodeStore = oauthCodeStore;
	}

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}

		User user = User.create(
				email,
				passwordEncoder.encode(request.password()),
				request.name().trim()
		);

		try {
			return SignupResponse.from(userRepository.saveAndFlush(user));
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		String email = normalizeEmail(request.email());
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

		if (user.getProvider() != AuthProvider.LOCAL
				|| !passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		return authTokenIssuer.issue(user);
	}

	@Transactional(readOnly = true)
	public AuthResponse exchangeOAuthCode(OAuthCodeExchangeRequest request) {
		String userIdValue;
		try {
			userIdValue = oauthCodeStore.consume(request.code());
		} catch (DataAccessException exception) {
			throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
		}

		Long userId = parseOAuthUserId(userIdValue);
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OAUTH_CODE));
		return authTokenIssuer.issue(user);
	}

	@Transactional(readOnly = true)
	public TokenResponse refresh(RefreshRequest request) {
		RefreshTokenClaims claims;
		try {
			claims = jwtProvider.parseRefreshToken(request.refreshToken());
		} catch (JwtException | IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		String storedToken;
		try {
			storedToken = refreshTokenStore.find(claims.userId());
		} catch (DataAccessException exception) {
			throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
		}

		if (!tokensMatch(storedToken, request.refreshToken())) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		User user = userRepository.findById(claims.userId())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
		return new TokenResponse(jwtProvider.generateAccessToken(user));
	}

	public void logout(CustomUserPrincipal principal) {
		Duration remainingTtl = Duration.between(Instant.now(), principal.expiresAt());
		if (remainingTtl.toMillis() <= 0) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}

		try {
			tokenRevocationStore.revoke(principal.userId(), principal.tokenId(), remainingTtl);
		} catch (DataAccessException exception) {
			throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
		}
	}

	private boolean tokensMatch(String storedToken, String requestedToken) {
		if (storedToken == null) {
			return false;
		}
		return MessageDigest.isEqual(
				storedToken.getBytes(StandardCharsets.UTF_8),
				requestedToken.getBytes(StandardCharsets.UTF_8)
		);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private Long parseOAuthUserId(String value) {
		if (value == null) {
			throw new BusinessException(ErrorCode.INVALID_OAUTH_CODE);
		}
		try {
			long userId = Long.parseLong(value);
			if (userId <= 0) {
				throw new NumberFormatException("non-positive user id");
			}
			return userId;
		} catch (NumberFormatException exception) {
			throw new BusinessException(ErrorCode.INVALID_OAUTH_CODE);
		}
	}
}
