package com.finrisk.radar.auth.oauth;

import com.finrisk.radar.user.AuthProvider;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

	private static final String GOOGLE_REGISTRATION_ID = "google";
	private static final int RANDOM_PASSWORD_BYTES = 32;
	private static final int MAX_EMAIL_LENGTH = 255;
	private static final int MAX_NAME_LENGTH = 50;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final SecureRandom secureRandom = new SecureRandom();

	public CustomOAuth2UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		if (!GOOGLE_REGISTRATION_ID.equals(userRequest.getClientRegistration().getRegistrationId())) {
			throw authenticationException("unsupported_oauth_provider");
		}

		OAuth2User oauthUser = super.loadUser(userRequest);
		Map<String, Object> attributes = oauthUser.getAttributes();
		String providerId = requiredString(attributes, "sub", "missing_google_subject");
		String email = normalizeEmail(requiredString(attributes, "email", "missing_google_email"));
		if (!isVerified(attributes.get("email_verified"))) {
			throw authenticationException("unverified_google_email");
		}
		String name = normalizeName(requiredString(attributes, "name", "missing_google_name"));

		User user = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
				.orElseGet(() -> createGoogleUser(email, name, providerId));
		return CustomOAuth2User.from(user, attributes);
	}

	private User createGoogleUser(String email, String name, String providerId) {
		if (userRepository.existsByEmail(email)) {
			throw authenticationException("oauth_email_conflict");
		}

		byte[] randomBytes = new byte[RANDOM_PASSWORD_BYTES];
		secureRandom.nextBytes(randomBytes);
		String randomPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		User user = User.createGoogle(email, passwordEncoder.encode(randomPassword), name, providerId);
		try {
			return userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException exception) {
			throw authenticationException("oauth_account_conflict");
		}
	}

	private String requiredString(Map<String, Object> attributes, String name, String errorCode) {
		Object value = attributes.get(name);
		if (!(value instanceof String stringValue) || stringValue.isBlank()) {
			throw authenticationException(errorCode);
		}
		return stringValue.trim();
	}

	private String normalizeEmail(String email) {
		String normalized = email.toLowerCase(Locale.ROOT);
		if (normalized.length() > MAX_EMAIL_LENGTH || !normalized.contains("@")) {
			throw authenticationException("invalid_google_email");
		}
		return normalized;
	}

	private String normalizeName(String name) {
		return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
	}

	private boolean isVerified(Object value) {
		return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
	}

	private OAuth2AuthenticationException authenticationException(String code) {
		return new OAuth2AuthenticationException(new OAuth2Error(code), code);
	}
}
