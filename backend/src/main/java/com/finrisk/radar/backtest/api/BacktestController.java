package com.finrisk.radar.backtest.api;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.backtest.BacktestStatus;
import com.finrisk.radar.backtest.service.BacktestQueryService;
import com.finrisk.radar.backtest.service.BacktestRequestService;
import com.finrisk.radar.backtest.service.NaturalLanguageBacktestService;
import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.usage.UsageLimit;
import com.finrisk.radar.usage.UsageType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backtests")
@SecurityRequirement(name = "bearerAuth")
public class BacktestController {
  private final BacktestRequestService requests;
  private final BacktestQueryService queries;
  private final NaturalLanguageBacktestService naturalLanguage;

  public BacktestController(
      BacktestRequestService requests,
      BacktestQueryService queries,
      NaturalLanguageBacktestService naturalLanguage) {
    this.requests = requests;
    this.queries = queries;
    this.naturalLanguage = naturalLanguage;
  }

  @PostMapping("/natural-language")
  public ResponseEntity<ApiResponse<NaturalLanguageBacktestResponse>> naturalLanguage(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody NaturalLanguageBacktestRequest request) {
    NaturalLanguageBacktestResponse response = naturalLanguage.request(principal.userId(), request);
    return ResponseEntity.status(
            "ACCEPTED".equals(response.outcome()) ? HttpStatus.ACCEPTED : HttpStatus.OK)
        .body(ApiResponse.success(response));
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
      @AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable UUID jobId) {
    return ApiResponse.success(queries.getForUser(jobId, principal.userId(), principal.role()));
  }

  @GetMapping
  public ApiResponse<BacktestPageResponse> list(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestParam(defaultValue = "COMPLETED") BacktestStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(queries.listForUser(principal.userId(), status, page, size));
  }
}
