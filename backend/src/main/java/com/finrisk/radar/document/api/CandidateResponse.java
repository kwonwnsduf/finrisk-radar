package com.finrisk.radar.document.api;

import com.finrisk.radar.document.*;
import com.finrisk.radar.risk.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public record CandidateResponse(
    Long id,
    Long assetId,
    CreditEventType eventType,
    LocalDate eventDate,
    RiskSeverity severity,
    BigDecimal confidence,
    CreditEventCandidateStatus status,
    Long approvedCreditEventId,
    RecalculationStatus recalculationStatus,
    UUID recalculationJobId,
    int recalculationAttemptCount,
    LocalDateTime recalculationLastAttemptedAt,
    String recalculationLastError,
    List<DocumentRiskMatchResponse> matches) {
  public static CandidateResponse from(CreditEventCandidate c, List<DocumentRiskMatch> matches) {
    return new CandidateResponse(
        c.getId(),
        c.getAssetId(),
        c.getEventType(),
        c.getEventDate(),
        c.getSeverity(),
        c.getConfidence(),
        c.getStatus(),
        c.getApprovedCreditEventId(),
        c.getRecalculationStatus(),
        c.getRecalculationJobId(),
        c.getRecalculationAttemptCount(),
        c.getRecalculationLastAttemptedAt(),
        c.getRecalculationLastError(),
        matches.stream().map(DocumentRiskMatchResponse::from).toList());
  }
}
