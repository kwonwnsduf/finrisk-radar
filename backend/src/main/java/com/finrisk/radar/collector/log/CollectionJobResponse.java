package com.finrisk.radar.collector.log;

import com.finrisk.radar.marketprice.MarketPriceSource;
import java.time.LocalDateTime;
import java.util.UUID;

public record CollectionJobResponse(UUID jobId, Long assetId, String ticker, MarketPriceSource source,
		CollectionStatus status, String message, String rawS3Path, LocalDateTime startedAt, LocalDateTime completedAt) {
	public static CollectionJobResponse from(CollectionLog log) {
		return new CollectionJobResponse(log.getJobId(), log.getAssetId(), log.getTicker(), log.getSource(),
				log.getStatus(), log.getMessage(), log.getRawS3Path(), log.getStartedAt(), log.getCompletedAt());
	}
}
