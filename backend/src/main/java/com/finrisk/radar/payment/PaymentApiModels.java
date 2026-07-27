package com.finrisk.radar.payment;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

record CreateOrderRequest(@NotBlank String productCode) {}

record ConfirmPaymentRequest(
    @NotBlank @Size(max = 200) String paymentKey,
    @NotBlank @Size(min = 6, max = 64) String orderId,
    @Positive long amount,
    @NotNull UUID idempotencyKey) {}

record CancelPaymentRequest(
    @NotBlank @Size(max = 200) String reason, @NotNull UUID cancelRequestId) {}

record PaymentOrderResponse(
    String orderId,
    String orderName,
    long amount,
    String currency,
    String customerKey,
    String customerName,
    String successUrl,
    String failUrl) {}

record PaymentResultResponse(
    String orderId,
    String orderStatus,
    long amount,
    String currency,
    String paymentKey,
    String paymentMethod,
    LocalDateTime approvedAt,
    LocalDateTime entitlementStart,
    LocalDateTime entitlementEnd,
    LocalDateTime subscriptionEnd,
    String currentPlan,
    boolean replayed,
    boolean recoveryRequired) {}

record PaymentCancelResponse(
    String orderId,
    String orderStatus,
    long amount,
    long removedUnusedSeconds,
    LocalDateTime contributionUsedUntil,
    LocalDateTime subscriptionEnd,
    String currentPlan,
    boolean replayed) {}

record PaymentHistoryItem(
    String orderId,
    String orderName,
    long amount,
    String currency,
    String orderStatus,
    String paymentMethod,
    LocalDateTime approvedAt,
    LocalDateTime canceledAt,
    String receiptUrl,
    LocalDateTime entitlementStart,
    LocalDateTime entitlementEnd,
    long entitlementRemainingSeconds,
    boolean cancellable) {}

record PaymentPageResponse<T>(
    List<T> items, int page, int size, long totalElements, int totalPages) {}
