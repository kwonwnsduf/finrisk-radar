package com.finrisk.radar.collector.event;

import java.time.Instant;
import java.util.UUID;

public record CollectionFailedEvent(UUID jobId, Long assetId, String ticker, String errorCode,
		String message, Instant failedAt) {}
