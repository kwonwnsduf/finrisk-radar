package com.finrisk.radar.rag.api;

import com.finrisk.radar.document.DocumentSourceType;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record EmbeddingRebuildRequest(
    @Size(max = 100) List<Long> documentIds,
    Long assetId,
    DocumentSourceType sourceType,
    LocalDate publishedFrom,
    LocalDate publishedTo,
    Boolean dryRun) {
  @AssertTrue(message = "A bounded rebuild scope is required")
  public boolean isScopePresent() {
    return (documentIds != null && !documentIds.isEmpty())
        || assetId != null
        || sourceType != null
        || publishedFrom != null
        || publishedTo != null;
  }

  @AssertTrue(message = "publishedFrom must not be after publishedTo")
  public boolean isDateRangeValid() {
    return publishedFrom == null || publishedTo == null || !publishedFrom.isAfter(publishedTo);
  }

  public boolean resolvedDryRun() {
    return dryRun == null || dryRun;
  }
}
