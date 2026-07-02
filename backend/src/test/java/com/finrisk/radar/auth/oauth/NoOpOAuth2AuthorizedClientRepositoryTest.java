package com.finrisk.radar.auth.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpOAuth2AuthorizedClientRepositoryTest {

	@Test
	void neverPersistsProviderAccessToken() {
		NoOpOAuth2AuthorizedClientRepository repository = new NoOpOAuth2AuthorizedClientRepository();
		ClientRegistration registration = ClientRegistration.withRegistrationId("google")
				.clientId("client")
				.clientSecret("secret")
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.authorizationUri("https://example.test/authorize")
				.tokenUri("https://example.test/token")
				.userInfoUri("https://example.test/userinfo")
				.userNameAttributeName("sub")
				.clientName("Google")
				.build();
		OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(
				registration,
				"principal",
				new OAuth2AccessToken(
						OAuth2AccessToken.TokenType.BEARER,
						"provider-access-token",
						Instant.now(),
						Instant.now().plusSeconds(300)));
		TestingAuthenticationToken authentication = new TestingAuthenticationToken("principal", null);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		repository.saveAuthorizedClient(client, authentication, request, response);

		assertThat(repository.<OAuth2AuthorizedClient>loadAuthorizedClient(
				"google", authentication, request)).isNull();
	}
}
