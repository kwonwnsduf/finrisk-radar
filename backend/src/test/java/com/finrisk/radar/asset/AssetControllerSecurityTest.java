package com.finrisk.radar.asset;

import com.finrisk.radar.auth.TokenRevocationStore;
import com.finrisk.radar.auth.jwt.AccessTokenClaims;
import com.finrisk.radar.auth.jwt.JwtAccessDeniedHandler;
import com.finrisk.radar.auth.jwt.JwtAuthenticationEntryPoint;
import com.finrisk.radar.auth.jwt.JwtAuthenticationFilter;
import com.finrisk.radar.auth.jwt.JwtProvider;
import com.finrisk.radar.auth.jwt.SecurityErrorResponseWriter;
import com.finrisk.radar.auth.oauth.CustomOAuth2UserService;
import com.finrisk.radar.auth.oauth.NoOpOAuth2AuthorizedClientRepository;
import com.finrisk.radar.auth.oauth.OAuthFailureHandler;
import com.finrisk.radar.auth.oauth.OAuthSuccessHandler;
import com.finrisk.radar.global.config.SecurityConfig;
import com.finrisk.radar.global.error.GlobalExceptionHandler;
import com.finrisk.radar.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssetController.class)
@Import({
		SecurityConfig.class,
		JwtAuthenticationFilter.class,
		JwtAuthenticationEntryPoint.class,
		JwtAccessDeniedHandler.class,
		SecurityErrorResponseWriter.class,
		NoOpOAuth2AuthorizedClientRepository.class,
		GlobalExceptionHandler.class
})
class AssetControllerSecurityTest {
	@Autowired MockMvc mockMvc;
	@MockitoBean AssetService assetService;
	@MockitoBean JwtProvider jwtProvider;
	@MockitoBean TokenRevocationStore tokenRevocationStore;
	@MockitoBean CustomOAuth2UserService customOAuth2UserService;
	@MockitoBean OAuthSuccessHandler oauthSuccessHandler;
	@MockitoBean OAuthFailureHandler oauthFailureHandler;
	@MockitoBean ClientRegistrationRepository clientRegistrationRepository;

	@Test
	void assetListIsPublic() throws Exception {
		when(assetService.getAll()).thenReturn(List.of());

		mockMvc.perform(get("/api/assets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void regularUserCannotCreateAsset() throws Exception {
		stubToken("user-token", Role.ROLE_USER);

		mockMvc.perform(post("/api/assets")
					.header("Authorization", "Bearer user-token")
					.contentType("application/json")
					.content("""
							{"name":"Samsung","ticker":"005930","market":"KOSPI","assetType":"STOCK"}
							"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_007"));
	}

	@Test
	void adminCanCreateAsset() throws Exception {
		stubToken("admin-token", Role.ROLE_ADMIN);
		when(assetService.create(any())).thenReturn(new AssetResponse(
				1L, "Samsung", "005930", "KOSPI", null, null, null, AssetType.STOCK));

		mockMvc.perform(post("/api/assets")
					.header("Authorization", "Bearer admin-token")
					.contentType("application/json")
					.content("""
							{"name":"Samsung","ticker":"005930","market":"KOSPI","assetType":"STOCK"}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.id").value(1));
	}

	private void stubToken(String token, Role role) {
		when(jwtProvider.parseAccessToken(token)).thenReturn(new AccessTokenClaims(
				42L, "user@example.com", role, "jti", Instant.now().plusSeconds(300)));
	}
}
