package com.finrisk.radar.collector.api;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.collector.log.CollectionJobResponse;
import com.finrisk.radar.collector.log.CollectionLogService;
import com.finrisk.radar.collector.service.CollectionRequestService;
import com.finrisk.radar.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/collector/market-prices")
@SecurityRequirement(name = "bearerAuth")
public class MarketPriceCollectorController {
	private final CollectionRequestService requests;
	private final CollectionLogService logs;
	public MarketPriceCollectorController(CollectionRequestService requests, CollectionLogService logs) {
		this.requests = requests; this.logs = logs;
	}
	@PostMapping("/fetch")
	public ResponseEntity<ApiResponse<MarketPriceFetchResponse>> fetch(
			@AuthenticationPrincipal CustomUserPrincipal principal,
			@Valid @RequestBody MarketPriceFetchRequest request) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(requests.request(principal.userId(), request)));
	}
	@GetMapping("/jobs/{jobId}")
	public ApiResponse<CollectionJobResponse> job(@AuthenticationPrincipal CustomUserPrincipal principal,
			@PathVariable UUID jobId) {
		return ApiResponse.success(logs.getForUser(jobId, principal.userId(), principal.role()));
	}
}
