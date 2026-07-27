package com.finrisk.radar.payment;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
  private final PaymentService payments;

  public PaymentController(PaymentService payments) {
    this.payments = payments;
  }

  @PostMapping("/orders")
  @Operation(summary = "Create a server-priced PREMIUM payment order")
  public ResponseEntity<ApiResponse<PaymentOrderResponse>> create(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody CreateOrderRequest request,
      @RequestHeader(name = "Idempotency-Key", required = false) UUID idempotencyKey,
      HttpServletRequest servletRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                payments.createOrder(
                    principal.userId(),
                    request,
                    idempotencyKey,
                    PaymentRequestMetadata.from(servletRequest))));
  }

  @PostMapping("/confirm")
  @Operation(summary = "Confirm an authenticated Toss payment idempotently")
  public ApiResponse<PaymentResultResponse> confirm(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody ConfirmPaymentRequest request,
      HttpServletRequest servletRequest) {
    return ApiResponse.success(
        payments.confirm(principal.userId(), request, PaymentRequestMetadata.from(servletRequest)));
  }

  @PostMapping("/{orderId}/cancel")
  @Operation(summary = "Cancel a payment and remove only its unused entitlement")
  public ApiResponse<PaymentCancelResponse> cancel(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @PathVariable String orderId,
      @Valid @RequestBody CancelPaymentRequest request,
      HttpServletRequest servletRequest) {
    return ApiResponse.success(
        payments.cancel(
            principal.userId(), orderId, request, PaymentRequestMetadata.from(servletRequest)));
  }

  @GetMapping("/me")
  public ApiResponse<PaymentPageResponse<PaymentHistoryItem>> history(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(payments.history(principal.userId(), page, size));
  }

  @GetMapping("/orders/{orderId}")
  public ApiResponse<PaymentHistoryItem> order(
      @AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable String orderId) {
    return ApiResponse.success(payments.getOrder(principal.userId(), orderId));
  }
}
