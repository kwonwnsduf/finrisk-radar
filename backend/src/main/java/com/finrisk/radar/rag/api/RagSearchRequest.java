package com.finrisk.radar.rag.api;

import com.finrisk.radar.document.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record RagSearchRequest(
    @NotBlank @Size(max = 500) String query,
    Long assetId,
    DocumentType documentType,
    DocumentSourceType sourceType,
    LocalDate publishedFrom,
    LocalDate publishedTo,
    @Min(1) @Max(20) Integer limit,
    @DecimalMin("0.0") @DecimalMax("1.0") Double minimumSimilarity) {

  @AssertTrue(message = "publishedFrom must not be after publishedTo")
  public boolean isDateRangeValid() {
    return publishedFrom == null || publishedTo == null || !publishedFrom.isAfter(publishedTo);
  }

  public int resolvedLimit() {
    return limit == null ? 5 : limit;
  }
}
