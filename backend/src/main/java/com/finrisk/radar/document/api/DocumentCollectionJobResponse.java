package com.finrisk.radar.document.api;

import com.finrisk.radar.document.*;
import java.util.UUID;

public record DocumentCollectionJobResponse(
    UUID jobId,
    Long assetId,
    DocumentSourceType sourceType,
    DocumentCollectionStatus status,
    int documentCount,
    String failureCode,
    String failureMessage) {
  public static DocumentCollectionJobResponse from(DocumentCollectionJob j) {
    return new DocumentCollectionJobResponse(
        j.getJobId(),
        j.getAssetId(),
        j.getSourceType(),
        j.getStatus(),
        j.getDocumentCount(),
        j.getFailureCode(),
        j.getFailureMessage());
  }
}
