package com.finrisk.radar.admin;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finrisk.radar.auth.TokenRevocationStore;
import com.finrisk.radar.auth.jwt.*;
import com.finrisk.radar.auth.oauth.*;
import com.finrisk.radar.global.config.SecurityConfig;
import com.finrisk.radar.global.error.GlobalExceptionHandler;
import com.finrisk.radar.user.Role;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminDashboardController.class)
@Import({
  SecurityConfig.class,
  JwtAuthenticationFilter.class,
  JwtAuthenticationEntryPoint.class,
  JwtAccessDeniedHandler.class,
  SecurityErrorResponseWriter.class,
  NoOpOAuth2AuthorizedClientRepository.class,
  GlobalExceptionHandler.class
})
class AdminDashboardControllerSecurityTest {
  @Autowired MockMvc mockMvc;
  @MockitoBean AdminDashboardQueryService service;
  @MockitoBean JwtProvider jwtProvider;
  @MockitoBean TokenRevocationStore tokenRevocationStore;
  @MockitoBean CustomOAuth2UserService customOAuth2UserService;
  @MockitoBean OAuthSuccessHandler oauthSuccessHandler;
  @MockitoBean OAuthFailureHandler oauthFailureHandler;
  @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

  @Test
  void anonymousRequestIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/admin/dashboard"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_003"));
  }

  @Test
  void regularUserIsForbidden() throws Exception {
    token("user", Role.ROLE_USER);

    mockMvc
        .perform(get("/api/admin/dashboard").header("Authorization", "Bearer user"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_007"));
  }

  @Test
  void adminCanReadDashboard() throws Exception {
    token("admin", Role.ROLE_ADMIN);
    LocalDateTime now = LocalDateTime.of(2026, 7, 27, 12, 0);
    when(service.get())
        .thenReturn(
            new AdminDashboardResponse(
                now,
                "Asia/Seoul",
                now.minusHours(24),
                now.minusDays(7),
                new AdminDashboardResponse.Users(10, 7, 3, 3, 1, 2, 1),
                new AdminDashboardResponse.Payments(
                    List.of(new AdminDashboardResponse.Money("KRW", 2, 11800)),
                    List.of(),
                    1,
                    2,
                    List.of(),
                    1,
                    1),
                new AdminDashboardResponse.Jobs(1, 1, 1, 1, 1, 1, 1),
                new AdminDashboardResponse.Reviews(1, 0, 2, 1, 1)));

    mockMvc
        .perform(get("/api/admin/dashboard").header("Authorization", "Bearer admin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.users.total").value(10))
        .andExpect(jsonPath("$.data.payments.approvedLast24Hours[0].currency").value("KRW"));
  }

  private void token(String token, Role role) {
    when(jwtProvider.parseAccessToken(token))
        .thenReturn(
            new AccessTokenClaims(
                42L, "admin@example.com", role, "jti", Instant.now().plusSeconds(300)));
  }
}
