package com.finrisk.radar.auth.oauth;

import com.finrisk.radar.user.AuthProvider;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

	private static final String USER_INFO_URI = "https://example.test/google/userinfo";

	@Mock
	private UserRepository userRepository;

	private PasswordEncoder passwordEncoder;
	private CustomOAuth2UserService userService;
	private MockRestServiceServer server;

	@BeforeEach
	void setUp() {
		passwordEncoder = new BCryptPasswordEncoder();
		userService = new CustomOAuth2UserService(userRepository, passwordEncoder);
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		userService.setRestOperations(restTemplate);
	}

	@Test
	void createsGoogleUserWithRandomBcryptPassword() {
		stubGoogleResponse(true);
		when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.empty());
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			ReflectionTestUtils.setField(user, "id", 42L);
			return user;
		});

		OAuth2User result = userService.loadUser(googleRequest());

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(captor.capture());
		User saved = captor.getValue();
		assertThat(saved.getProvider()).isEqualTo(AuthProvider.GOOGLE);
		assertThat(saved.getProviderId()).isEqualTo("google-subject");
		assertThat(saved.getPassword()).isNotBlank().startsWith("$2");
		assertThat(saved.getPassword()).doesNotContain("provider-access-token");
		assertThat(result).isInstanceOf(CustomOAuth2User.class);
		assertThat(((CustomOAuth2User) result).getUserId()).isEqualTo(42L);
		server.verify();
	}

	@Test
	void reusesExistingGoogleAccountWithoutUpdatingOrSaving() {
		stubGoogleResponse(true);
		User existing = User.createGoogle("user@example.com", "encoded-password", "User", "google-subject");
		ReflectionTestUtils.setField(existing, "id", 42L);
		when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.of(existing));

		OAuth2User result = userService.loadUser(googleRequest());

		assertThat(((CustomOAuth2User) result).getUserId()).isEqualTo(42L);
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void rejectsLocalEmailCollisionWithoutAutomaticLinking() {
		stubGoogleResponse(true);
		when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.empty());
		when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.loadUser(googleRequest()))
				.isInstanceOf(OAuth2AuthenticationException.class)
				.extracting(exception -> ((OAuth2AuthenticationException) exception).getError().getErrorCode())
				.isEqualTo("oauth_email_conflict");
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void rejectsUnverifiedGoogleEmail() {
		stubGoogleResponse(false);

		assertThatThrownBy(() -> userService.loadUser(googleRequest()))
				.isInstanceOf(OAuth2AuthenticationException.class)
				.extracting(exception -> ((OAuth2AuthenticationException) exception).getError().getErrorCode())
				.isEqualTo("unverified_google_email");
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	private void stubGoogleResponse(boolean verified) {
		String body = """
				{"sub":"google-subject","email":"User@Example.COM","email_verified":%s,"name":"Google User"}
				""".formatted(verified);
		server.expect(requestTo(USER_INFO_URI))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	private OAuth2UserRequest googleRequest() {
		ClientRegistration registration = ClientRegistration.withRegistrationId("google")
				.clientId("test-client")
				.clientSecret("test-secret")
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope("email", "profile")
				.authorizationUri("https://example.test/google/authorize")
				.tokenUri("https://example.test/google/token")
				.userInfoUri(USER_INFO_URI)
				.userNameAttributeName("sub")
				.clientName("Google")
				.build();
		OAuth2AccessToken accessToken = new OAuth2AccessToken(
				OAuth2AccessToken.TokenType.BEARER,
				"provider-access-token",
				Instant.now(),
				Instant.now().plusSeconds(300),
				Set.of("email", "profile")
		);
		return new OAuth2UserRequest(registration, accessToken);
	}
}
