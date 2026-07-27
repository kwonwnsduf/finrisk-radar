package com.finrisk.radar.admin;

import com.finrisk.radar.fsd.*;
import com.finrisk.radar.payment.PaymentOrderStatus;
import java.time.LocalDateTime;
import java.util.List;

record AdminPaymentItem(
    String orderId,
    Long userId,
    String email,
    String name,
    String productCode,
    String orderName,
    long amount,
    String currency,
    PaymentOrderStatus status,
    LocalDateTime createdAt,
    LocalDateTime paidAt,
    LocalDateTime canceledAt,
    String latestFailureCode,
    FsdStatus fsdStatus,
    FsdSeverity fsdSeverity,
    boolean recoveryRequired) {}

record AdminPaymentAttempt(
    String attemptType,
    String result,
    String errorCode,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime completedAt) {}

record AdminPaymentCancellation(
    String cancelReason,
    long amount,
    String status,
    LocalDateTime requestedAt,
    LocalDateTime completedAt,
    LocalDateTime failedAt) {}

record AdminPaymentDetail(
    AdminPaymentItem payment,
    List<AdminPaymentAttempt> attempts,
    AdminPaymentCancellation cancellation,
    AdminPaymentAttempt latestReconciliation) {}
