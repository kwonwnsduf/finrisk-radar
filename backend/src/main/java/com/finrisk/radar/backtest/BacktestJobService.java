package com.finrisk.radar.backtest;

import com.finrisk.radar.backtest.engine.BacktestCalculationResult;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class BacktestJobService {
	private final BacktestJobRepository jobs;
	private final BacktestResultRepository results;

	public BacktestJobService(BacktestJobRepository jobs, BacktestResultRepository results) {
		this.jobs = jobs;
		this.results = results;
	}

	@Transactional
	public BacktestJob createRequested(Long userId, Long assetId, StrategyType strategyType,
			LocalDate startDate, LocalDate endDate) {
		return jobs.save(BacktestJob.requested(userId, assetId, strategyType, startDate, endDate));
	}

	@Transactional
	public boolean markRunning(UUID jobId) { return find(jobId).start(); }

	@Transactional
	public void completeWithResult(UUID jobId, BacktestCalculationResult calculation) {
		BacktestJob job = find(jobId);
		if (job.getStatus() != BacktestStatus.RUNNING) throw new IllegalStateException("Backtest is not running.");
		results.save(BacktestResult.from(jobId, calculation));
		job.complete();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailed(UUID jobId, String message) {
		String safe = message == null || message.isBlank() ? "Backtest failed." : message;
		find(jobId).fail(safe.substring(0, Math.min(safe.length(), 1000)));
	}

	@Transactional(readOnly = true)
	public BacktestJob getInternal(UUID jobId) { return find(jobId); }

	private BacktestJob find(UUID jobId) {
		return jobs.findById(jobId).orElseThrow(() -> new BusinessException(ErrorCode.BACKTEST_JOB_NOT_FOUND));
	}
}
