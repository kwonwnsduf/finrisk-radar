package com.finrisk.radar.rag;

import com.finrisk.radar.document.*;
import java.time.LocalDate;

public record RagSearchCriteria(
    Long assetId,
    DocumentType documentType,
    DocumentSourceType sourceType,
    LocalDate publishedFrom,
    LocalDate publishedTo,
    int limit,
    Double minimumSimilarity) {}
