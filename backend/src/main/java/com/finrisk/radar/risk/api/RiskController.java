package com.finrisk.radar.risk.api;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.risk.RiskCalculationJob;
import com.finrisk.radar.risk.service.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/risks")
@SecurityRequirement(name = "bearerAuth")
public class RiskController {
  private final RiskCalculationRequestService requests;
  private final RiskQueryService queries;

  public RiskController(RiskCalculationRequestService r, RiskQueryService q) {
    requests = r;
    queries = q;
  }

  @PostMapping("/assets/{assetId}/calculations")
  @Operation(summary = "Request asynchronous corporate risk calculation")
  public ResponseEntity<ApiResponse<RiskJobResponse>> calculate(
      @AuthenticationPrincipal CustomUserPrincipal p, @PathVariable Long assetId) {
    RiskCalculationJob j = requests.request(p.userId(), assetId);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(RiskJobResponse.from(j, null)));
  }

  @GetMapping("/jobs/{jobId}")
  public ApiResponse<RiskJobResponse> job(
      @AuthenticationPrincipal CustomUserPrincipal p, @PathVariable UUID jobId) {
    return ApiResponse.success(queries.job(jobId, p.userId(), p.role()));
  }

  @GetMapping("/assets/{assetId}/latest")
  public ApiResponse<RiskScoreResponse> latest(@PathVariable Long assetId) {
    return ApiResponse.success(queries.latest(assetId));
  }

  @GetMapping("/assets/{assetId}/history")
  public ApiResponse<List<RiskScoreResponse>> history(@PathVariable Long assetId) {
    return ApiResponse.success(queries.history(assetId));
  }

  @GetMapping("/{riskScoreId}")
  public ApiResponse<RiskScoreResponse> score(@PathVariable Long riskScoreId) {
    return ApiResponse.success(queries.score(riskScoreId));
  }

  @GetMapping("/{riskScoreId}/signals")
  public ApiResponse<List<RiskSignalResponse>> signals(@PathVariable Long riskScoreId) {
    return ApiResponse.success(queries.signalResponses(riskScoreId));
  }
}
