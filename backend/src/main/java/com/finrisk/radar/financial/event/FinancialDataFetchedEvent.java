package com.finrisk.radar.financial.event;

import java.time.Instant;
import java.util.UUID;

public record FinancialDataFetchedEvent(
    UUID jobId,
    Long assetId,
    String stockCode,
    String corpCode,
    Integer year,
    Integer quarter,
    String statementDivision,
    boolean fallbackUsed,
    int recordCount,
    String rawS3Path,
    UUID riskCalculationJobId,
    Instant completedAt) {}
