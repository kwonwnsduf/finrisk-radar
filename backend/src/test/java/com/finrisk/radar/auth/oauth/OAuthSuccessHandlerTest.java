package com.finrisk.radar.auth.oauth;

import com.finrisk.radar.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuthSuccessHandlerTest {

	@Mock
	private OAuthCodeStore codeStore;

	private OAuthSuccessHandler successHandler;

	@BeforeEach
	void setUp() {
		OAuthProperties properties = new OAuthProperties();
		properties.setFrontendRedirectUri(URI.create("http://localhost:3000/login"));
		successHandler = new OAuthSuccessHandler(codeStore, properties);
	}

	@Test
	void redirectsWithOnlyOpaqueOneTimeCode() throws Exception {
		User user = User.createGoogle("user@example.com", "encoded-password", "User", "google-subject");
		ReflectionTestUtils.setField(user, "id", 42L);
		CustomOAuth2User principal = CustomOAuth2User.from(user, Map.of("sub", "google-subject"));
		TestingAuthenticationToken authentication = new TestingAuthenticationToken(principal, null);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession(true);
		MockHttpServletResponse response = new MockHttpServletResponse();

		successHandler.onAuthenticationSuccess(request, response, authentication);

		ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
		verify(codeStore).save(codeCaptor.capture(), eq(42L));
		assertThat(codeCaptor.getValue()).matches("[A-Za-z0-9_-]{43}");
		assertThat(response.getRedirectedUrl())
				.startsWith("http://localhost:3000/login?oauthCode=")
				.doesNotContain("accessToken", "refreshToken", "provider-access-token");
	}

	@Test
	void redirectsWithGenericErrorWhenRedisIsUnavailable() throws Exception {
		doThrow(new DataAccessResourceFailureException("redis unavailable"))
				.when(codeStore).save(anyString(), eq(42L));
		User user = User.createGoogle("user@example.com", "encoded-password", "User", "google-subject");
		ReflectionTestUtils.setField(user, "id", 42L);
		CustomOAuth2User principal = CustomOAuth2User.from(user, Map.of("sub", "google-subject"));
		TestingAuthenticationToken authentication = new TestingAuthenticationToken(principal, null);
		MockHttpServletResponse response = new MockHttpServletResponse();

		successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

		assertThat(response.getRedirectedUrl())
				.isEqualTo("http://localhost:3000/login?oauthError=service_unavailable");
	}
}
