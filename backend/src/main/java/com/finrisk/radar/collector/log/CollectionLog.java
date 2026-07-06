package com.finrisk.radar.collector.log;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import com.finrisk.radar.marketprice.MarketPriceSource;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "collection_logs")
public class CollectionLog extends BaseTimeEntity {
	@Id @Column(name = "job_id", nullable = false, updatable = false)
	private UUID jobId;
	@Column(name = "asset_id", nullable = false, updatable = false)
	private Long assetId;
	@Column(name = "requested_by_user_id", nullable = false, updatable = false)
	private Long requestedByUserId;
	@Column(nullable = false, length = 100, updatable = false)
	private String ticker;
	@Column(name = "start_date", nullable = false, updatable = false)
	private LocalDate startDate;
	@Column(name = "end_date", nullable = false, updatable = false)
	private LocalDate endDate;
	@Enumerated(EnumType.STRING) @Column(length = 20)
	private MarketPriceSource source;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
	private CollectionStatus status;
	@Column(length = 1000)
	private String message;
	@Column(name = "raw_s3_path", length = 1000)
	private String rawS3Path;
	@Column(name = "started_at")
	private LocalDateTime startedAt;
	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	protected CollectionLog() {}
	private CollectionLog(UUID jobId, Long assetId, Long userId, String ticker, LocalDate startDate, LocalDate endDate) {
		this.jobId = jobId; this.assetId = assetId; this.requestedByUserId = userId; this.ticker = ticker;
		this.startDate = startDate; this.endDate = endDate; this.status = CollectionStatus.REQUESTED;
		this.message = "Collection requested.";
	}
	public static CollectionLog requested(Long assetId, Long userId, String ticker, LocalDate startDate, LocalDate endDate) {
		return new CollectionLog(UUID.randomUUID(), assetId, userId, ticker, startDate, endDate);
	}
	public boolean start() {
		if (status != CollectionStatus.REQUESTED) return false;
		status = CollectionStatus.RUNNING; startedAt = LocalDateTime.now(); message = "Collection is running."; return true;
	}
	public void rawStored(MarketPriceSource source, String path) {
		if (status != CollectionStatus.RUNNING) throw new IllegalStateException("Collection is not running.");
		this.source = source; this.rawS3Path = path; this.message = "Raw market data stored.";
	}
	public void complete(MarketPriceSource source, int count) {
		if (status != CollectionStatus.RUNNING) throw new IllegalStateException("Collection is not running.");
		this.source = source; status = CollectionStatus.COMPLETED; completedAt = LocalDateTime.now();
		message = count + " market price records stored.";
	}
	public void fail(String safeMessage) {
		if (status == CollectionStatus.COMPLETED || status == CollectionStatus.FAILED) return;
		status = CollectionStatus.FAILED; completedAt = LocalDateTime.now(); message = safeMessage;
	}
	public UUID getJobId() { return jobId; }
	public Long getAssetId() { return assetId; }
	public Long getRequestedByUserId() { return requestedByUserId; }
	public String getTicker() { return ticker; }
	public LocalDate getStartDate() { return startDate; }
	public LocalDate getEndDate() { return endDate; }
	public MarketPriceSource getSource() { return source; }
	public CollectionStatus getStatus() { return status; }
	public String getMessage() { return message; }
	public String getRawS3Path() { return rawS3Path; }
	public LocalDateTime getStartedAt() { return startedAt; }
	public LocalDateTime getCompletedAt() { return completedAt; }
}
