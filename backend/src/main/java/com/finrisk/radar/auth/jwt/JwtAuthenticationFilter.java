package com.finrisk.radar.auth.jwt;

import com.finrisk.radar.auth.TokenRevocationStore;
import com.finrisk.radar.global.error.ErrorCode;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtProvider jwtProvider;
	private final TokenRevocationStore tokenRevocationStore;
	private final SecurityErrorResponseWriter errorResponseWriter;

	public JwtAuthenticationFilter(
			JwtProvider jwtProvider,
			TokenRevocationStore tokenRevocationStore,
			SecurityErrorResponseWriter errorResponseWriter
	) {
		this.jwtProvider = jwtProvider;
		this.tokenRevocationStore = tokenRevocationStore;
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String token = resolveBearerToken(request);
		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			try {
				AccessTokenClaims claims = jwtProvider.parseAccessToken(token);
				if (tokenRevocationStore.isBlacklisted(claims.tokenId())) {
					SecurityContextHolder.clearContext();
					filterChain.doFilter(request, response);
					return;
				}
				CustomUserPrincipal principal = CustomUserPrincipal.from(claims);
				UsernamePasswordAuthenticationToken authentication =
						UsernamePasswordAuthenticationToken.authenticated(
								principal,
								null,
								principal.authorities()
						);
				authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (DataAccessException exception) {
				SecurityContextHolder.clearContext();
				errorResponseWriter.write(response, ErrorCode.AUTH_SERVICE_UNAVAILABLE);
				return;
			} catch (JwtException | IllegalArgumentException exception) {
				SecurityContextHolder.clearContext();
			}
		}

		filterChain.doFilter(request, response);
	}

	private String resolveBearerToken(HttpServletRequest request) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			return null;
		}

		String token = authorization.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? null : token;
	}
}
