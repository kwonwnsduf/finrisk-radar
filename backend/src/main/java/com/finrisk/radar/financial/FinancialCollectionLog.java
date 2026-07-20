package com.finrisk.radar.financial;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "financial_collection_logs")
public class FinancialCollectionLog extends BaseTimeEntity {
  @Id
  @Column(name = "job_id", nullable = false, updatable = false)
  private UUID jobId;

  @Column(name = "asset_id", nullable = false, updatable = false)
  private Long assetId;

  @Column(name = "requested_by_user_id", nullable = false, updatable = false)
  private Long requestedByUserId;

  @Column(name = "risk_calculation_job_id", updatable = false)
  private UUID riskCalculationJobId;

  @Column(name = "stock_code", nullable = false, length = 20, updatable = false)
  private String stockCode;

  @Column(name = "corp_code", length = 20)
  private String corpCode;

  @Column(nullable = false, updatable = false)
  private Integer year;

  @Column(nullable = false, updatable = false)
  private Integer quarter;

  @Column(name = "statement_division", length = 10)
  private String statementDivision;

  @Column(name = "fallback_used", nullable = false)
  private boolean fallbackUsed;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FinancialCollectionStatus status;

  @Column(length = 1000)
  private String message;

  @Column(name = "raw_s3_path", length = 1000)
  private String rawS3Path;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  protected FinancialCollectionLog() {}

  private FinancialCollectionLog(
      UUID jobId,
      Long userId,
      Long assetId,
      String stockCode,
      Integer year,
      Integer quarter,
      UUID riskCalculationJobId) {
    this.jobId = jobId;
    this.requestedByUserId = userId;
    this.assetId = assetId;
    this.stockCode = stockCode;
    this.year = year;
    this.quarter = quarter;
    this.riskCalculationJobId = riskCalculationJobId;
    this.status = FinancialCollectionStatus.REQUESTED;
    this.message = "Financial data collection requested.";
  }

  public static FinancialCollectionLog requested(
      Long userId, Long assetId, String stockCode, Integer year, Integer quarter) {
    return requested(userId, assetId, stockCode, year, quarter, null);
  }

  public static FinancialCollectionLog requested(
      Long userId,
      Long assetId,
      String stockCode,
      Integer year,
      Integer quarter,
      UUID riskCalculationJobId) {
    return new FinancialCollectionLog(
        UUID.randomUUID(), userId, assetId, stockCode, year, quarter, riskCalculationJobId);
  }

  public boolean start() {
    if (status == FinancialCollectionStatus.RUNNING) return true;
    if (status != FinancialCollectionStatus.REQUESTED) return false;
    status = FinancialCollectionStatus.RUNNING;
    startedAt = LocalDateTime.now();
    message = "Financial data collection is running.";
    return true;
  }

  public void rawStored(
      String corpCode, String statementDivision, boolean fallbackUsed, String path) {
    if (status != FinancialCollectionStatus.RUNNING)
      throw new IllegalStateException("Financial collection is not running.");
    this.corpCode = corpCode;
    this.statementDivision = statementDivision;
    this.fallbackUsed = fallbackUsed;
    this.rawS3Path = path;
    this.message = "Raw DART financial data stored.";
  }

  public void complete(int count) {
    if (status != FinancialCollectionStatus.RUNNING)
      throw new IllegalStateException("Financial collection is not running.");
    status = FinancialCollectionStatus.COMPLETED;
    completedAt = LocalDateTime.now();
    message = count + " financial metric records stored.";
  }

  public void fail(String safeMessage) {
    if (status == FinancialCollectionStatus.COMPLETED || status == FinancialCollectionStatus.FAILED)
      return;
    status = FinancialCollectionStatus.FAILED;
    completedAt = LocalDateTime.now();
    message = safeMessage;
  }

  public UUID getJobId() {
    return jobId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public Long getRequestedByUserId() {
    return requestedByUserId;
  }

  public UUID getRiskCalculationJobId() {
    return riskCalculationJobId;
  }

  public String getStockCode() {
    return stockCode;
  }

  public String getCorpCode() {
    return corpCode;
  }

  public Integer getYear() {
    return year;
  }

  public Integer getQuarter() {
    return quarter;
  }

  public String getStatementDivision() {
    return statementDivision;
  }

  public boolean isFallbackUsed() {
    return fallbackUsed;
  }

  public FinancialCollectionStatus getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public String getRawS3Path() {
    return rawS3Path;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }
}
