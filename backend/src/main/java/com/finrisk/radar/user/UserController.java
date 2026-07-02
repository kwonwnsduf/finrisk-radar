package com.finrisk.radar.user;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.usage.UsageQueryService;
import com.finrisk.radar.usage.UsageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;
	private final UsageQueryService usageQueryService;

	public UserController(UserService userService, UsageQueryService usageQueryService) {
		this.userService = userService;
		this.usageQueryService = usageQueryService;
	}

	@GetMapping("/me")
	public ApiResponse<MeResponse> me(@AuthenticationPrincipal CustomUserPrincipal principal) {
		return ApiResponse.success(userService.getMe(principal.userId()));
	}

	@GetMapping("/me/usage")
	public ApiResponse<UsageResponse> usage(@AuthenticationPrincipal CustomUserPrincipal principal) {
		return ApiResponse.success(usageQueryService.getUsage(principal.userId()));
	}
}
