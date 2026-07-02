package com.finrisk.radar.auth.jwt;

import com.finrisk.radar.user.User;
import com.finrisk.radar.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

	private static final String ISSUER = "finrisk-radar";
	private static final String TOKEN_TYPE_CLAIM = "tokenType";
	private static final int MINIMUM_SECRET_BYTES = 32;

	private final SecretKey signingKey;
	private final JwtParser accessTokenParser;
	private final JwtParser refreshTokenParser;
	private final Duration accessTokenExpiration;
	private final Duration refreshTokenExpiration;

	public JwtProvider(JwtProperties properties) {
		this.signingKey = createSigningKey(properties.getSecret());
		this.accessTokenParser = Jwts.parser()
				.verifyWith(signingKey)
				.requireIssuer(ISSUER)
				.require(TOKEN_TYPE_CLAIM, "ACCESS")
				.build();
		this.refreshTokenParser = Jwts.parser()
				.verifyWith(signingKey)
				.requireIssuer(ISSUER)
				.require(TOKEN_TYPE_CLAIM, "REFRESH")
				.build();
		this.accessTokenExpiration = requirePositive(
				properties.getAccessTokenExpiration(),
				"jwt.access-token-expiration"
		);
		this.refreshTokenExpiration = requirePositive(
				properties.getRefreshTokenExpiration(),
				"jwt.refresh-token-expiration"
		);
	}

	public TokenPair generateTokens(User user) {
		Instant issuedAt = Instant.now();
		return new TokenPair(
				generateAccessToken(user, issuedAt),
				generateRefreshToken(user, issuedAt)
		);
	}

	public AccessTokenClaims parseAccessToken(String token) {
		Jws<Claims> parsed = accessTokenParser.parseSignedClaims(token);
		if (!"HS256".equals(parsed.getHeader().getAlgorithm())) {
			throw new UnsupportedJwtException("Only HS256 access tokens are supported.");
		}

		Claims claims = parsed.getPayload();
		Long userId = parseUserId(claims.getSubject());
		String email = claims.get("email", String.class);
		if (email == null || email.isBlank()) {
			throw new MalformedJwtException("Access token email claim is missing.");
		}

		Role role;
		try {
			role = Role.valueOf(claims.get("role", String.class));
		} catch (RuntimeException exception) {
			throw new MalformedJwtException("Access token role claim is invalid.", exception);
		}

		String tokenId = claims.getId();
		if (tokenId == null || tokenId.isBlank()) {
			throw new MalformedJwtException("Access token ID claim is missing.");
		}
		Date expiration = claims.getExpiration();
		if (expiration == null) {
			throw new MalformedJwtException("Access token expiration claim is missing.");
		}

		return new AccessTokenClaims(userId, email, role, tokenId, expiration.toInstant());
	}

	public RefreshTokenClaims parseRefreshToken(String token) {
		Jws<Claims> parsed = refreshTokenParser.parseSignedClaims(token);
		if (!"HS256".equals(parsed.getHeader().getAlgorithm())) {
			throw new UnsupportedJwtException("Only HS256 refresh tokens are supported.");
		}
		return new RefreshTokenClaims(parseUserId(parsed.getPayload().getSubject()));
	}

	public String generateAccessToken(User user) {
		return generateAccessToken(user, Instant.now());
	}

	private String generateAccessToken(User user, Instant issuedAt) {
		return Jwts.builder()
				.issuer(ISSUER)
				.subject(String.valueOf(user.getId()))
				.id(UUID.randomUUID().toString())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(issuedAt.plus(accessTokenExpiration)))
				.claim(TOKEN_TYPE_CLAIM, "ACCESS")
				.claim("email", user.getEmail())
				.claim("role", user.getRole().name())
				.signWith(signingKey, Jwts.SIG.HS256)
				.compact();
	}

	private String generateRefreshToken(User user, Instant issuedAt) {
		return Jwts.builder()
				.issuer(ISSUER)
				.subject(String.valueOf(user.getId()))
				.id(UUID.randomUUID().toString())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(issuedAt.plus(refreshTokenExpiration)))
				.claim(TOKEN_TYPE_CLAIM, "REFRESH")
				.signWith(signingKey, Jwts.SIG.HS256)
				.compact();
	}

	private SecretKey createSigningKey(String encodedSecret) {
		if (encodedSecret == null || encodedSecret.isBlank()) {
			throw new IllegalStateException("JWT secret must be configured.");
		}

		byte[] secretBytes;
		try {
			secretBytes = Decoders.BASE64.decode(encodedSecret);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("JWT secret must be valid Base64.", exception);
		}

		if (secretBytes.length < MINIMUM_SECRET_BYTES) {
			throw new IllegalStateException("JWT secret must contain at least 256 bits.");
		}
		return Keys.hmacShaKeyFor(secretBytes);
	}

	private Long parseUserId(String subject) {
		try {
			long userId = Long.parseLong(subject);
			if (userId <= 0) {
				throw new NumberFormatException("User ID must be positive.");
			}
			return userId;
		} catch (RuntimeException exception) {
			throw new MalformedJwtException("Access token subject is invalid.", exception);
		}
	}

	private Duration requirePositive(Duration duration, String propertyName) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new IllegalStateException(propertyName + " must be positive.");
		}
		return duration;
	}
}
