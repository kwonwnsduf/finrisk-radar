package com.finrisk.radar.rag.api;

import com.finrisk.radar.rag.*;
import java.time.LocalDateTime;
import java.util.UUID;

public record EmbeddingJobResponse(
    UUID jobId,
    Long documentId,
    int contentVersion,
    String embeddingModel,
    int embeddingDimensions,
    EmbeddingJobStatus status,
    boolean active,
    int attemptCount,
    int chunkCount,
    String failureCode,
    String failureMessage,
    LocalDateTime requestedAt,
    LocalDateTime completedAt) {
  public static EmbeddingJobResponse from(DocumentEmbeddingJob job) {
    return new EmbeddingJobResponse(
        job.getJobId(),
        job.getDocumentId(),
        job.getContentVersion(),
        job.getEmbeddingModel(),
        job.getEmbeddingDimensions(),
        job.getStatus(),
        job.isActive(),
        job.getAttemptCount(),
        job.getChunkCount(),
        job.getFailureCode(),
        job.getFailureMessage(),
        job.getRequestedAt(),
        job.getCompletedAt());
  }
}
