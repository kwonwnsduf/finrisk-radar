package com.finrisk.radar.financial.event;

import java.time.Instant;
import java.util.UUID;

public record FinancialDataFetchFailedEvent(
    UUID jobId,
    Long assetId,
    String stockCode,
    Integer year,
    Integer quarter,
    String errorCode,
    String message,
    UUID riskCalculationJobId,
    Instant failedAt) {}
