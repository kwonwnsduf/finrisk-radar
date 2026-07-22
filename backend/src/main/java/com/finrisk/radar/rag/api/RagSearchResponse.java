package com.finrisk.radar.rag.api;

import com.finrisk.radar.document.*;
import com.finrisk.radar.rag.RagSearchHit;
import java.time.LocalDateTime;
import java.util.List;

public record RagSearchResponse(
    Long chunkId,
    Long documentId,
    int chunkIndex,
    List<RagAssetResponse> assets,
    String documentTitle,
    String chunkContent,
    double similarity,
    DocumentType documentType,
    DocumentSourceType sourceType,
    DocumentContentScope contentScope,
    String sourceName,
    String sourceUrl,
    LocalDateTime publishedAt,
    List<RagRiskMatchResponse> riskMatches) {
  public static RagSearchResponse from(
      RagSearchHit hit, List<RagAssetResponse> assets, List<RagRiskMatchResponse> matches) {
    return new RagSearchResponse(
        hit.chunkId(),
        hit.documentId(),
        hit.chunkIndex(),
        assets,
        hit.documentTitle(),
        hit.chunkContent(),
        hit.similarity(),
        hit.documentType(),
        hit.sourceType(),
        hit.contentScope(),
        hit.sourceName(),
        hit.sourceUrl(),
        hit.publishedAt(),
        matches);
  }
}
