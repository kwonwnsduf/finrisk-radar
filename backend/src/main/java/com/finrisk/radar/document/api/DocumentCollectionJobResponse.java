package com.finrisk.radar.document.api;

import com.finrisk.radar.document.*;
import java.time.*;
import java.util.UUID;

public record DocumentCollectionJobResponse(
    UUID jobId,
    Long assetId,
    DocumentSourceType sourceType,
    LocalDate fromDate,
    LocalDate toDate,
    DocumentCollectionStatus status,
    int documentCount,
    String failureCode,
    String failureMessage,
    LocalDateTime requestedAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt) {
  public static DocumentCollectionJobResponse from(DocumentCollectionJob j) {
    return new DocumentCollectionJobResponse(
        j.getJobId(),
        j.getAssetId(),
        j.getSourceType(),
        j.getFromDate(),
        j.getToDate(),
        j.getStatus(),
        j.getDocumentCount(),
        j.getFailureCode(),
        j.getFailureMessage(),
        j.getRequestedAt(),
        j.getStartedAt(),
        j.getCompletedAt());
  }
}
