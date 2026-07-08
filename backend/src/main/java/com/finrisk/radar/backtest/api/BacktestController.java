package com.finrisk.radar.backtest.api;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.backtest.service.BacktestQueryService;
import com.finrisk.radar.backtest.service.BacktestRequestService;
import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.usage.UsageLimit;
import com.finrisk.radar.usage.UsageType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/backtests")
@SecurityRequirement(name = "bearerAuth")
public class BacktestController {
	private final BacktestRequestService requests;
	private final BacktestQueryService queries;

	public BacktestController(BacktestRequestService requests, BacktestQueryService queries) {
		this.requests = requests;
		this.queries = queries;
	}

	@Operation(summary = "Request an asynchronous backtest with a basic or custom strategy")
	@UsageLimit(UsageType.BACKTEST)
	@PostMapping
	public ResponseEntity<ApiResponse<BacktestCreateResponse>> create(
			@AuthenticationPrincipal CustomUserPrincipal principal,
			@Valid @RequestBody BacktestCreateRequest request) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(requests.request(principal.userId(), request)));
	}

	@Operation(summary = "Get backtest job status and completed result")
	@GetMapping("/{jobId}")
	public ApiResponse<BacktestJobResponse> job(
			@AuthenticationPrincipal CustomUserPrincipal principal,
			@PathVariable UUID jobId) {
		return ApiResponse.success(queries.getForUser(jobId, principal.userId(), principal.role()));
	}
}
