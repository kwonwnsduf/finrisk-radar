package com.finrisk.radar.financial;

import java.util.UUID;

public record FinancialMetricFetchResponse(UUID jobId, FinancialCollectionStatus status) {}
