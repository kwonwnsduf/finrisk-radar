package com.finrisk.radar.document.api;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.document.*;
import com.finrisk.radar.document.service.CreditEventReviewService;
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

  public CreditEventCandidateAdminController(
      CreditEventCandidateRepository candidates,
      DocumentRiskMatchRepository matches,
      CreditEventReviewService reviews) {
    this.candidates = candidates;
    this.matches = matches;
    this.reviews = reviews;
  }

  @GetMapping
  public ApiResponse<List<CandidateResponse>> list(
      @RequestParam(defaultValue = "PENDING_REVIEW") CreditEventCandidateStatus status) {
    return ApiResponse.success(
        candidates.findByStatusOrderByConfidenceDescEventDateDesc(status).stream()
            .map(this::response)
            .toList());
  }

  @GetMapping("/{id}")
  public ApiResponse<CandidateResponse> get(@PathVariable Long id) {
    return ApiResponse.success(response(candidates.findById(id).orElseThrow()));
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

  private CandidateResponse response(CreditEventCandidate c) {
    return CandidateResponse.from(c, matches.findByCandidateIdOrderByConfidenceDesc(c.getId()));
  }
}
