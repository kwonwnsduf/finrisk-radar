package com.finrisk.radar.document.event;

import com.finrisk.radar.document.DocumentSourceType;
import java.time.*;
import java.util.UUID;

public record DocumentFetchRequestedEvent(
    int eventVersion,
    UUID correlationId,
    UUID jobId,
    Long assetId,
    DocumentSourceType sourceType,
    LocalDate fromDate,
    LocalDate toDate,
    Instant occurredAt) {}
