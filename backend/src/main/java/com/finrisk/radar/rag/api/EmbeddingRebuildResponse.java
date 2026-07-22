package com.finrisk.radar.rag.api;

import java.util.List;

public record EmbeddingRebuildResponse(
    boolean dryRun, int candidateCount, List<Long> documentIds, List<EmbeddingJobResponse> jobs) {}
