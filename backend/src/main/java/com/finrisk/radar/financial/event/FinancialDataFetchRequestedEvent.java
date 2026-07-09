package com.finrisk.radar.financial.event;

import java.time.Instant;
import java.util.UUID;

public record FinancialDataFetchRequestedEvent(
		UUID jobId,
		Long assetId,
		String stockCode,
		Integer year,
		Integer quarter,
		Instant requestedAt
) {}
