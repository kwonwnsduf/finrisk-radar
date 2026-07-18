package com.finrisk.radar.document.event;

import java.time.Instant;
import java.util.UUID;

public record DocumentCollectionFailedEvent(
    int eventVersion,
    UUID correlationId,
    UUID jobId,
    Long assetId,
    String failureCode,
    String message,
    Instant occurredAt) {}
