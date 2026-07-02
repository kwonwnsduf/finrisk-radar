package com.finrisk.radar.auth.jwt;

import com.finrisk.radar.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

	private static final String SECRET =
			"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@Test
	void generatesSignedAccessAndRefreshTokensWithExpectedClaims() {
		JwtProvider provider = new JwtProvider(properties(SECRET));
		User user = User.create("user@example.com", "encoded-password", "User");
		ReflectionTestUtils.setField(user, "id", 42L);

		TokenPair tokens = provider.generateTokens(user);
		Jws<Claims> access = parse(tokens.accessToken());
		Jws<Claims> refresh = parse(tokens.refreshToken());

		assertThat(access.getHeader().getAlgorithm()).isEqualTo("HS256");
		assertThat(refresh.getHeader().getAlgorithm()).isEqualTo("HS256");

		Claims accessClaims = access.getPayload();
		assertThat(accessClaims.getIssuer()).isEqualTo("finrisk-radar");
		assertThat(accessClaims.getSubject()).isEqualTo("42");
		assertThat(accessClaims.get("tokenType", String.class)).isEqualTo("ACCESS");
		assertThat(accessClaims.get("email", String.class)).isEqualTo("user@example.com");
		assertThat(accessClaims.get("role", String.class)).isEqualTo("ROLE_USER");
		assertThat(accessClaims.getId()).isNotBlank();
		assertThat(Duration.between(
				accessClaims.getIssuedAt().toInstant(),
				accessClaims.getExpiration().toInstant()
		)).isEqualTo(Duration.ofMinutes(30));

		Claims refreshClaims = refresh.getPayload();
		assertThat(refreshClaims.getIssuer()).isEqualTo("finrisk-radar");
		assertThat(refreshClaims.getSubject()).isEqualTo("42");
		assertThat(refreshClaims.get("tokenType", String.class)).isEqualTo("REFRESH");
		assertThat(refreshClaims).doesNotContainKeys("email", "role");
		assertThat(refreshClaims.getId()).isNotBlank().isNotEqualTo(accessClaims.getId());
		assertThat(Duration.between(
				refreshClaims.getIssuedAt().toInstant(),
				refreshClaims.getExpiration().toInstant()
		)).isEqualTo(Duration.ofDays(14));
		assertThat(tokens.accessToken()).isNotEqualTo(tokens.refreshToken());
	}

	@Test
	void generatesUniqueTokenIdsForEachIssue() {
		JwtProvider provider = new JwtProvider(properties(SECRET));
		User user = User.create("user@example.com", "encoded-password", "User");
		ReflectionTestUtils.setField(user, "id", 42L);

		TokenPair first = provider.generateTokens(user);
		TokenPair second = provider.generateTokens(user);

		assertThat(parse(first.accessToken()).getPayload().getId())
				.isNotEqualTo(parse(second.accessToken()).getPayload().getId());
		assertThat(parse(first.refreshToken()).getPayload().getId())
				.isNotEqualTo(parse(second.refreshToken()).getPayload().getId());
	}

	@Test
	void parsesValidAccessTokenIntoTypedClaims() {
		JwtProvider provider = new JwtProvider(properties(SECRET));
		User user = User.create("user@example.com", "encoded-password", "User");
		ReflectionTestUtils.setField(user, "id", 42L);

		AccessTokenClaims claims = provider.parseAccessToken(
				provider.generateTokens(user).accessToken()
		);

		assertThat(claims.userId()).isEqualTo(42L);
		assertThat(claims.email()).isEqualTo("user@example.com");
		assertThat(claims.role().name()).isEqualTo("ROLE_USER");
		assertThat(claims.tokenId()).isNotBlank();
		assertThat(claims.expiresAt()).isAfter(Instant.now());
	}

	@Test
	void rejectsAccessTokensWithoutJtiOrExpiration() {
		JwtProvider provider = new JwtProvider(properties(SECRET));
		Instant now = Instant.now();
		SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
		String missingJti = Jwts.builder()
				.issuer("finrisk-radar")
				.subject("42")
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(Duration.ofMinutes(30))))
				.claim("tokenType", "ACCESS")
				.claim("email", "user@example.com")
				.claim("role", "ROLE_USER")
				.signWith(key, Jwts.SIG.HS256)
				.compact();
		String missingExpiration = Jwts.builder()
				.issuer("finrisk-radar")
				.subject("42")
				.id(UUID.randomUUID().toString())
				.issuedAt(Date.from(now))
				.claim("tokenType", "ACCESS")
				.claim("email", "user@example.com")
				.claim("role", "ROLE_USER")
				.signWith(key, Jwts.SIG.HS256)
				.compact();

		assertThatThrownBy(() -> provider.parseAccessToken(missingJti))
				.isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> provider.parseAccessToken(missingExpiration))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void parsesValidRefreshTokenAndRejectsAccessToken() {
		JwtProvider provider = new JwtProvider(properties(SECRET));
		User user = User.create("user@example.com", "encoded-password", "User");
		ReflectionTestUtils.setField(user, "id", 42L);
		TokenPair tokens = provider.generateTokens(user);

		RefreshTokenClaims claims = provider.parseRefreshToken(tokens.refreshToken());

		assertThat(claims.userId()).isEqualTo(42L);
		assertThatThrownBy(() -> provider.parseRefreshToken(tokens.accessToken()))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void generatesStandaloneAccessTokenWithExpectedClaims() {
		JwtProvider provider = new JwtProvider(properties(SECRET));
		User user = User.create("user@example.com", "encoded-password", "User");
		ReflectionTestUtils.setField(user, "id", 42L);

		Claims claims = parse(provider.generateAccessToken(user)).getPayload();

		assertThat(claims.getSubject()).isEqualTo("42");
		assertThat(claims.get("tokenType", String.class)).isEqualTo("ACCESS");
		assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
		assertThat(claims.get("role", String.class)).isEqualTo("ROLE_USER");
	}

	@Test
	void rejectsExpiredAndInvalidSubjectRefreshTokens() {
		JwtProvider provider = new JwtProvider(properties(SECRET));
		Instant now = Instant.now();
		String expired = signedRefreshToken(
				"finrisk-radar", "42",
				now.minus(Duration.ofDays(15)), now.minus(Duration.ofDays(1))
		);
		String invalidSubject = signedRefreshToken(
				"finrisk-radar", "not-a-number",
				now, now.plus(Duration.ofDays(14))
		);

		assertThatThrownBy(() -> provider.parseRefreshToken(expired)).isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> provider.parseRefreshToken(invalidSubject)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsRefreshTokenAsAccessToken() {
		JwtProvider provider = new JwtProvider(properties(SECRET));
		User user = User.create("user@example.com", "encoded-password", "User");
		ReflectionTestUtils.setField(user, "id", 42L);

		String refreshToken = provider.generateTokens(user).refreshToken();

		assertThatThrownBy(() -> provider.parseAccessToken(refreshToken))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsExpiredTamperedAndInvalidClaimAccessTokens() {
		JwtProvider provider = new JwtProvider(properties(SECRET));
		Instant now = Instant.now();
		String expired = signedAccessToken(
				"finrisk-radar", "42", "user@example.com", "ROLE_USER",
				now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1))
		);
		String wrongIssuer = signedAccessToken(
				"another-issuer", "42", "user@example.com", "ROLE_USER",
				now, now.plus(Duration.ofMinutes(30))
		);
		String wrongRole = signedAccessToken(
				"finrisk-radar", "42", "user@example.com", "NOT_A_ROLE",
				now, now.plus(Duration.ofMinutes(30))
		);
		String valid = signedAccessToken(
				"finrisk-radar", "42", "user@example.com", "ROLE_USER",
				now, now.plus(Duration.ofMinutes(30))
		);
		int signatureStart = valid.lastIndexOf('.') + 1;
		char replacement = valid.charAt(signatureStart) == 'A' ? 'B' : 'A';
		String tampered = valid.substring(0, signatureStart)
				+ replacement
				+ valid.substring(signatureStart + 1);

		assertThatThrownBy(() -> provider.parseAccessToken(expired)).isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> provider.parseAccessToken(wrongIssuer)).isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> provider.parseAccessToken(wrongRole)).isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> provider.parseAccessToken(tampered)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsInvalidBase64Secret() {
		assertThatThrownBy(() -> new JwtProvider(properties("%%%not-base64%%%")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("JWT secret must be valid Base64.");
	}

	@Test
	void rejectsMissingSecret() {
		assertThatThrownBy(() -> new JwtProvider(properties(null)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("JWT secret must be configured.");
	}

	@Test
	void rejectsSecretShorterThan256Bits() {
		String weakSecret = Base64.getEncoder().encodeToString(new byte[16]);

		assertThatThrownBy(() -> new JwtProvider(properties(weakSecret)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("JWT secret must contain at least 256 bits.");
	}

	private JwtProperties properties(String secret) {
		JwtProperties properties = new JwtProperties();
		properties.setSecret(secret);
		properties.setAccessTokenExpiration(Duration.ofMinutes(30));
		properties.setRefreshTokenExpiration(Duration.ofDays(14));
		return properties;
	}

	private Jws<Claims> parse(String token) {
		SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
		return Jwts.parser()
				.verifyWith(key)
				.requireIssuer("finrisk-radar")
				.build()
				.parseSignedClaims(token);
	}

	private String signedAccessToken(
			String issuer,
			String subject,
			String email,
			String role,
			Instant issuedAt,
			Instant expiration
	) {
		SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
		return Jwts.builder()
				.issuer(issuer)
				.subject(subject)
				.id(UUID.randomUUID().toString())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(expiration))
				.claim("tokenType", "ACCESS")
				.claim("email", email)
				.claim("role", role)
				.signWith(key, Jwts.SIG.HS256)
				.compact();
	}

	private String signedRefreshToken(
			String issuer,
			String subject,
			Instant issuedAt,
			Instant expiration
	) {
		SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
		return Jwts.builder()
				.issuer(issuer)
				.subject(subject)
				.id(UUID.randomUUID().toString())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(expiration))
				.claim("tokenType", "REFRESH")
				.signWith(key, Jwts.SIG.HS256)
				.compact();
	}
}
