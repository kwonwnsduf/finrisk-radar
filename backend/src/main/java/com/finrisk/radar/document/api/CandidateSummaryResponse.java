package com.finrisk.radar.document.api;

import com.finrisk.radar.document.CreditEventCandidateStatus;
import com.finrisk.radar.risk.*;
import java.math.BigDecimal;
import java.time.*;

public record CandidateSummaryResponse(
    Long id,
    Long assetId,
    String assetName,
    String ticker,
    CreditEventType eventType,
    LocalDate eventDate,
    RiskSeverity severity,
    BigDecimal confidence,
    String documentTitle,
    String documentSource,
    CreditEventCandidateStatus status,
    LocalDateTime createdAt) {}
