package com.finrisk.radar.document.api;

import com.finrisk.radar.document.*;
import java.time.LocalDateTime;
import java.util.List;

public record DocumentResponse(
    Long id,
    DocumentType documentType,
    DocumentSourceType sourceType,
    String sourceName,
    String title,
    String content,
    String summary,
    String sourceUrl,
    LocalDateTime publishedAt,
    int contentVersion,
    List<Long> assetIds,
    int riskMatchCount) {
  public static DocumentResponse from(Document d, List<Long> assets, int count) {
    return new DocumentResponse(
        d.getId(),
        d.getDocumentType(),
        d.getSourceType(),
        d.getSourceName(),
        d.getTitle(),
        d.getContent(),
        d.getSummary(),
        d.getSourceUrl(),
        d.getPublishedAt(),
        d.getContentVersion(),
        assets,
        count);
  }
}
