package com.finrisk.radar.backtest.api;

import com.finrisk.radar.backtest.BacktestJob;
import com.finrisk.radar.backtest.BacktestStatus;
import com.finrisk.radar.backtest.StrategyType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record BacktestJobResponse(
		UUID jobId,
		Long assetId,
		StrategyType strategyType,
		LocalDate startDate,
		LocalDate endDate,
		BacktestStatus status,
		String message,
		LocalDateTime startedAt,
		LocalDateTime completedAt,
		BacktestResultResponse result
) {
	public static BacktestJobResponse from(BacktestJob job, BacktestResultResponse result) {
		return new BacktestJobResponse(job.getJobId(), job.getAssetId(), job.getStrategyType(),
				job.getStartDate(), job.getEndDate(), job.getStatus(), job.getMessage(),
				job.getStartedAt(), job.getCompletedAt(), result);
	}
}
