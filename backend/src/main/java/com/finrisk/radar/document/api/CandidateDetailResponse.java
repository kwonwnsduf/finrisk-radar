package com.finrisk.radar.document.api;

import com.finrisk.radar.document.*;
import com.finrisk.radar.risk.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record CandidateDetailResponse(
    Long id,
    Long assetId,
    String assetName,
    String ticker,
    CreditEventType eventType,
    LocalDate eventDate,
    RiskSeverity severity,
    BigDecimal confidence,
    CreditEventCandidateStatus status,
    Long reviewedBy,
    LocalDateTime reviewedAt,
    String reviewNote,
    LocalDateTime createdAt,
    List<Match> matches,
    List<Nearby> nearbyCandidates) {
  public record Match(
      Long id,
      Long documentId,
      String documentTitle,
      String sourceType,
      String sourceName,
      String sourceUrl,
      String sentenceText,
      String matchedText,
      DocumentAssertionType assertionType,
      BigDecimal confidence,
      BigDecimal extractedAmount,
      String extractedCurrency,
      String evidence) {}

  public record Nearby(
      Long id,
      LocalDate eventDate,
      RiskSeverity severity,
      BigDecimal confidence,
      CreditEventCandidateStatus status) {}
}
