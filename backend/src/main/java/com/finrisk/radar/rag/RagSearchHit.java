package com.finrisk.radar.rag;

import com.finrisk.radar.document.*;
import java.time.LocalDateTime;

public record RagSearchHit(
    Long chunkId,
    Long documentId,
    int chunkIndex,
    int sentenceStartIndex,
    int sentenceEndIndex,
    String documentTitle,
    String chunkContent,
    double similarity,
    DocumentType documentType,
    DocumentSourceType sourceType,
    DocumentContentScope contentScope,
    String sourceName,
    String sourceUrl,
    LocalDateTime publishedAt) {}
