package com.finrisk.radar.collector.api;

import com.finrisk.radar.collector.log.CollectionStatus;
import java.util.UUID;

public record MarketPriceFetchResponse(UUID jobId, CollectionStatus status) {}
