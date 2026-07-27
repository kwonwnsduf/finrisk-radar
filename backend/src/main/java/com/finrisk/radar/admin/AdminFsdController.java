package com.finrisk.radar.admin;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.fsd.*;
import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.payment.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import com.finrisk.radar.payment.PaymentOrderStatus;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminFsdController {
  private final AdminFsdService fsd;
  private final PaymentService payments;

  public AdminFsdController(AdminFsdService fsd, PaymentService payments) {
    this.fsd = fsd;
    this.payments = payments;
  }

  @GetMapping("/fsd-events")
  public ApiResponse<AdminPage<FsdEventResponse>> events(
      @RequestParam(required = false) FsdStatus status,
      @RequestParam(required = false) FsdSeverity severity,
      @RequestParam(required = false) FsdDecision decision,
      @RequestParam(required = false) String ruleCode,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) LocalDateTime from,
      @RequestParam(required = false) LocalDateTime to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(
        fsd.list(status, severity, decision, ruleCode, search, from, to, page, size));
  }

  @GetMapping("/fsd-events/{id}")
  public ApiResponse<FsdEventResponse> event(@PathVariable Long id) {
    return ApiResponse.success(fsd.get(id));
  }

  @PatchMapping("/fsd-events/{id}")
  public ApiResponse<FsdEventResponse> review(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @PathVariable Long id,
      @Valid @RequestBody ReviewFsdRequest request) {
    return ApiResponse.success(
        fsd.review(id, request.status(), request.reviewNote(), principal.userId()));
  }

  @PostMapping("/payments/{orderId}/reconcile")
  public ApiResponse<ReconcileResponse> reconcile(@PathVariable String orderId) {
    return ApiResponse.success(new ReconcileResponse(orderId, payments.reconcile(orderId)));
  }
}

record ReviewFsdRequest(FsdStatus status, @Size(max = 1000) String reviewNote) {}

record ReconcileResponse(String orderId, String result) {}

record FsdEventResponse(
    Long id,
    String orderId,
    Long userId,
    String userEmail,
    String userName,
    Long amount,
    String currency,
    PaymentOrderStatus paymentStatus,
    String ruleCode,
    FsdPhase phase,
    FsdDecision decision,
    FsdSeverity severity,
    int score,
    String reason,
    Object evidence,
    FsdStatus status,
    LocalDateTime detectedAt,
    LocalDateTime reviewedAt,
    Long reviewedBy,
    String reviewNote,
    List<AdminPaymentAttempt> attempts) {
  static FsdEventResponse from(
      FsdEvent value,
      String orderId,
      String userEmail,
      String userName,
      Long amount,
      String currency,
      PaymentOrderStatus paymentStatus,
      List<AdminPaymentAttempt> attempts) {
    return new FsdEventResponse(
        value.getId(),
        orderId,
        value.getUserId(),
        userEmail,
        userName,
        amount,
        currency,
        paymentStatus,
        value.getRuleCode(),
        value.getPhase(),
        value.getDecision(),
        value.getSeverity(),
        value.getScore(),
        value.getReason(),
        value.getEvidence(),
        value.getStatus(),
        value.getDetectedAt(),
        value.getReviewedAt(),
        value.getReviewedBy(),
        value.getReviewNote(),
        attempts);
  }
}
