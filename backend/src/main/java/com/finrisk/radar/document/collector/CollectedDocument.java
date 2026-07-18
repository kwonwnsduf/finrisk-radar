package com.finrisk.radar.document.collector;

import com.finrisk.radar.document.*;
import java.time.LocalDateTime;

public record CollectedDocument(
    DocumentType documentType,
    DocumentSourceType sourceType,
    String sourceName,
    String title,
    String content,
    String summary,
    String sourceUrl,
    String externalId,
    LocalDateTime publishedAt,
    byte[] rawPayload,
    String rawContentType,
    String rawExtension,
    byte[] extractedSourcePayload,
    String extractedSourceContentType,
    String extractedSourceExtension,
    String corpCode) {}
