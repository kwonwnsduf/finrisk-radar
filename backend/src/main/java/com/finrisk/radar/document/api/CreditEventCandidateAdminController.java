package com.finrisk.radar.document.api;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.document.*;
import com.finrisk.radar.document.service.CreditEventReviewService;
import com.finrisk.radar.document.service.CreditEventCandidateQueryService;
import com.finrisk.radar.document.service.DocumentRiskRecalculationCoordinator;
import com.finrisk.radar.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/credit-event-candidates")
public class CreditEventCandidateAdminController {
  private final CreditEventCandidateRepository candidates;
  private final DocumentRiskMatchRepository matches;
  private final CreditEventReviewService reviews;
  private final DocumentRiskRecalculationCoordinator recalculations;
  private final CreditEventCandidateQueryService queries;

  public CreditEventCandidateAdminController(
      CreditEventCandidateRepository candidates,
      DocumentRiskMatchRepository matches,
      CreditEventReviewService reviews,
      DocumentRiskRecalculationCoordinator recalculations,
      CreditEventCandidateQueryService queries) {
    this.candidates = candidates;
    this.matches = matches;
    this.reviews = reviews;
    this.recalculations = recalculations;
    this.queries = queries;
  }

  @GetMapping
  public ApiResponse<com.finrisk.radar.admin.AdminPage<CandidateSummaryResponse>> list(
      @RequestParam(defaultValue = "PENDING_REVIEW") CreditEventCandidateStatus status,
      @RequestParam(required = false) com.finrisk.radar.risk.RiskSeverity severity,
      @RequestParam(required = false) Long assetId,
      @RequestParam(required = false) com.finrisk.radar.risk.CreditEventType eventType,
      @RequestParam(required = false) java.time.LocalDateTime from,
      @RequestParam(required = false) java.time.LocalDateTime to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(
        queries.list(status, severity, assetId, eventType, from, to, page, size));
  }

  @GetMapping("/{id}")
  public ApiResponse<CandidateDetailResponse> get(@PathVariable Long id) {
    return ApiResponse.success(queries.get(id));
  }

  @PostMapping("/{id}/approve")
  public ApiResponse<CandidateResponse> approve(
      @AuthenticationPrincipal CustomUserPrincipal p,
      @PathVariable Long id,
      @Valid @RequestBody CandidateReviewRequest r) {
    return ApiResponse.success(response(reviews.approve(id, p.userId(), r.reviewNote())));
  }

  @PostMapping("/{id}/reject")
  public ApiResponse<CandidateResponse> reject(
      @AuthenticationPrincipal CustomUserPrincipal p,
      @PathVariable Long id,
      @Valid @RequestBody CandidateReviewRequest r) {
    return ApiResponse.success(response(reviews.reject(id, p.userId(), r.reviewNote())));
  }

  @PostMapping("/{id}/recalculate")
  public ApiResponse<CandidateResponse> recalculate(
      @AuthenticationPrincipal CustomUserPrincipal p, @PathVariable Long id) {
    recalculations.retryNow(id, p.userId());
    return ApiResponse.success(response(candidates.findById(id).orElseThrow()));
  }

  private CandidateResponse response(CreditEventCandidate c) {
    return CandidateResponse.from(c, matches.findByCandidateIdOrderByConfidenceDesc(c.getId()));
  }
}
