package com.finrisk.radar.document.event;

import java.time.Instant;
import java.util.UUID;

public record DocumentRiskAnalyzedEvent(
    int eventVersion,
    UUID correlationId,
    Long documentId,
    int matchCount,
    int candidateCount,
    Instant occurredAt) {}
