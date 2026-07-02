package com.finrisk.radar.subscription;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.global.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
	private final SubscriptionService subscriptionService;

	public SubscriptionController(SubscriptionService subscriptionService) {
		this.subscriptionService = subscriptionService;
	}

	@GetMapping("/me")
	public ApiResponse<SubscriptionResponse> me(@AuthenticationPrincipal CustomUserPrincipal principal) {
		return ApiResponse.success(subscriptionService.getCurrent(principal.userId()));
	}
}
