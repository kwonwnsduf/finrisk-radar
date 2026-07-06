package com.finrisk.radar.collector.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MarketDataFetchRequestedEvent(UUID jobId, Long assetId, String ticker,
		LocalDate startDate, LocalDate endDate, Instant requestedAt) {}
