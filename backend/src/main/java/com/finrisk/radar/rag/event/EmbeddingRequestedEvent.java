package com.finrisk.radar.rag.event;

import java.time.Instant;
import java.util.UUID;

public record EmbeddingRequestedEvent(
    int eventVersion,
    UUID correlationId,
    UUID jobId,
    Long documentId,
    int contentVersion,
    String embeddingModel,
    int embeddingDimensions,
    Instant occurredAt) {}
