package com.finrisk.radar.global.config;

import com.finrisk.radar.auth.jwt.JwtAuthenticationEntryPoint;
import com.finrisk.radar.auth.jwt.JwtAuthenticationFilter;
import com.finrisk.radar.auth.oauth.CustomOAuth2UserService;
import com.finrisk.radar.auth.oauth.NoOpOAuth2AuthorizedClientRepository;
import com.finrisk.radar.auth.oauth.OAuthFailureHandler;
import com.finrisk.radar.auth.oauth.OAuthSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	private final CustomOAuth2UserService customOAuth2UserService;
	private final OAuthSuccessHandler oauthSuccessHandler;
	private final OAuthFailureHandler oauthFailureHandler;
	private final NoOpOAuth2AuthorizedClientRepository authorizedClientRepository;

	public SecurityConfig(
			JwtAuthenticationFilter jwtAuthenticationFilter,
			JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
			CustomOAuth2UserService customOAuth2UserService,
			OAuthSuccessHandler oauthSuccessHandler,
			OAuthFailureHandler oauthFailureHandler,
			NoOpOAuth2AuthorizedClientRepository authorizedClientRepository
	) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
		this.customOAuth2UserService = customOAuth2UserService;
		this.oauthSuccessHandler = oauthSuccessHandler;
		this.oauthFailureHandler = oauthFailureHandler;
		this.authorizedClientRepository = authorizedClientRepository;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint(jwtAuthenticationEntryPoint))
				.requestCache(cache -> cache.requestCache(new NullRequestCache()))
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.oauth2Login(oauth -> oauth
						.authorizedClientRepository(authorizedClientRepository)
						.userInfoEndpoint(userInfo -> userInfo
								.userService(customOAuth2UserService))
						.successHandler(oauthSuccessHandler)
						.failureHandler(oauthFailureHandler))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/api/auth/signup",
								"/api/auth/login",
								"/api/auth/refresh",
								"/api/auth/oauth/exchange",
								"/oauth2/authorization/google",
								"/login/oauth2/code/google",
								"/api/health",
								"/actuator/health",
								"/actuator/prometheus",
								"/swagger-ui.html",
								"/swagger-ui/**",
								"/v3/api-docs/**")
						.permitAll()
						.requestMatchers("/api/users/me").authenticated()
						.requestMatchers("/api/auth/logout").authenticated()
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

}
