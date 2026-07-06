package com.finrisk.radar.backtest;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "backtest_jobs")
public class BacktestJob extends BaseTimeEntity {
	@Id @Column(name = "job_id", nullable = false, updatable = false)
	private UUID jobId;
	@Column(name = "requested_by_user_id", nullable = false, updatable = false)
	private Long requestedByUserId;
	@Column(name = "asset_id", nullable = false, updatable = false)
	private Long assetId;
	@Enumerated(EnumType.STRING) @Column(name = "strategy_type", nullable = false, length = 30, updatable = false)
	private StrategyType strategyType;
	@Column(name = "start_date", nullable = false, updatable = false)
	private LocalDate startDate;
	@Column(name = "end_date", nullable = false, updatable = false)
	private LocalDate endDate;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
	private BacktestStatus status;
	@Column(length = 1000)
	private String message;
	@Column(name = "started_at")
	private LocalDateTime startedAt;
	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	protected BacktestJob() {}

	private BacktestJob(UUID jobId, Long userId, Long assetId, StrategyType strategyType,
			LocalDate startDate, LocalDate endDate) {
		this.jobId = jobId;
		this.requestedByUserId = userId;
		this.assetId = assetId;
		this.strategyType = strategyType;
		this.startDate = startDate;
		this.endDate = endDate;
		this.status = BacktestStatus.REQUESTED;
		this.message = "Backtest requested.";
	}

	public static BacktestJob requested(Long userId, Long assetId, StrategyType strategyType,
			LocalDate startDate, LocalDate endDate) {
		return new BacktestJob(UUID.randomUUID(), userId, assetId, strategyType, startDate, endDate);
	}

	public boolean start() {
		if (status != BacktestStatus.REQUESTED) return false;
		status = BacktestStatus.RUNNING;
		startedAt = LocalDateTime.now();
		message = "Backtest is running.";
		return true;
	}

	public void complete() {
		if (status != BacktestStatus.RUNNING) throw new IllegalStateException("Backtest is not running.");
		status = BacktestStatus.COMPLETED;
		completedAt = LocalDateTime.now();
		message = "Backtest completed.";
	}

	public void fail(String safeMessage) {
		if (status == BacktestStatus.COMPLETED || status == BacktestStatus.FAILED) return;
		status = BacktestStatus.FAILED;
		completedAt = LocalDateTime.now();
		message = safeMessage;
	}

	public UUID getJobId() { return jobId; }
	public Long getRequestedByUserId() { return requestedByUserId; }
	public Long getAssetId() { return assetId; }
	public StrategyType getStrategyType() { return strategyType; }
	public LocalDate getStartDate() { return startDate; }
	public LocalDate getEndDate() { return endDate; }
	public BacktestStatus getStatus() { return status; }
	public String getMessage() { return message; }
	public LocalDateTime getStartedAt() { return startedAt; }
	public LocalDateTime getCompletedAt() { return completedAt; }
}
