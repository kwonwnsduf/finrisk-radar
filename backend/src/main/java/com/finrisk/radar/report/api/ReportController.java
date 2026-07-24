package com.finrisk.radar.report.api;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.report.*;
import com.finrisk.radar.report.service.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
  private final ReportRequestService requests;
  private final ReportQueryService queries;

  public ReportController(ReportRequestService requests, ReportQueryService queries) {
    this.requests = requests;
    this.queries = queries;
  }

  @PostMapping("/risk")
  public ResponseEntity<ApiResponse<ReportAcceptedResponse>> risk(
      @AuthenticationPrincipal CustomUserPrincipal p,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody RiskReportRequest r) {
    var c =
        requests.request(
            p.userId(), r.assetId(), null, ReportType.RISK_ANALYSIS, r.question(), key);
    return ResponseEntity.status(c.reused() ? HttpStatus.OK : HttpStatus.ACCEPTED)
        .body(ApiResponse.success(ReportAcceptedResponse.from(c.report(), c.reused())));
  }

  @PostMapping("/backtest")
  public ResponseEntity<ApiResponse<ReportAcceptedResponse>> backtest(
      @AuthenticationPrincipal CustomUserPrincipal p,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody BacktestReportRequest r) {
    var c =
        requests.request(
            p.userId(), null, r.backtestJobId(), ReportType.BACKTEST_ANALYSIS, r.question(), key);
    return ResponseEntity.status(c.reused() ? HttpStatus.OK : HttpStatus.ACCEPTED)
        .body(ApiResponse.success(ReportAcceptedResponse.from(c.report(), c.reused())));
  }

  @PostMapping("/watchlist-summary")
  public ResponseEntity<ApiResponse<ReportAcceptedResponse>> watchlist(
      @AuthenticationPrincipal CustomUserPrincipal p,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody WatchlistSummaryRequest r) {
    var c =
        requests.request(p.userId(), null, null, ReportType.WATCHLIST_SUMMARY, r.question(), key);
    return ResponseEntity.status(c.reused() ? HttpStatus.OK : HttpStatus.ACCEPTED)
        .body(ApiResponse.success(ReportAcceptedResponse.from(c.report(), c.reused())));
  }

  @GetMapping
  public ApiResponse<ReportPageResponse> list(
      @AuthenticationPrincipal CustomUserPrincipal p,
      @RequestParam(required = false) ReportType reportType,
      @RequestParam(required = false) ReportStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(queries.list(p.userId(), reportType, status, page, size));
  }

  @GetMapping("/{id}")
  public ApiResponse<ReportResponse> get(
      @AuthenticationPrincipal CustomUserPrincipal p, @PathVariable UUID id) {
    return ApiResponse.success(queries.get(id, p.userId(), p.role()));
  }
}
