package com.finrisk.radar.auth;

import com.finrisk.radar.auth.dto.AuthResponse;
import com.finrisk.radar.auth.dto.LoginRequest;
import com.finrisk.radar.auth.dto.OAuthCodeExchangeRequest;
import com.finrisk.radar.auth.dto.RefreshRequest;
import com.finrisk.radar.auth.dto.SignupRequest;
import com.finrisk.radar.auth.dto.SignupResponse;
import com.finrisk.radar.auth.dto.TokenResponse;
import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(ApiResponse.success(authService.signup(request)));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
	}

	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.refresh(request)));
	}

	@PostMapping("/oauth/exchange")
	public ResponseEntity<ApiResponse<AuthResponse>> exchangeOAuthCode(
			@Valid @RequestBody OAuthCodeExchangeRequest request
	) {
		return ResponseEntity.ok(ApiResponse.success(authService.exchangeOAuthCode(request)));
	}

	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(
			@AuthenticationPrincipal CustomUserPrincipal principal
	) {
		authService.logout(principal);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
