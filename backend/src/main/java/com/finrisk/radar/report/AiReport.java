package com.finrisk.radar.report;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import com.finrisk.radar.usage.UsageType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_reports")
public class AiReport extends BaseTimeEntity {
  @Id private UUID id;

  @Column(name = "requested_by_user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "asset_id", updatable = false)
  private Long assetId;

  @Column(name = "backtest_job_id", updatable = false)
  private UUID backtestJobId;

  @Enumerated(EnumType.STRING)
  @Column(name = "report_type", nullable = false, updatable = false)
  private ReportType reportType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReportStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "current_step")
  private ReportStep currentStep;

  @Column(length = 500, updatable = false)
  private String question;

  @Column(length = 300)
  private String title;

  @Column(columnDefinition = "text")
  private String content;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "structured_result", columnDefinition = "jsonb")
  private String structuredResult;

  @Column(length = 100)
  private String model;

  @Column(name = "prompt_version", nullable = false, length = 100, updatable = false)
  private String promptVersion;

  @Column(name = "input_token_count", nullable = false)
  private long inputTokenCount;

  @Column(name = "output_token_count", nullable = false)
  private long outputTokenCount;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "failure_code", length = 100)
  private String failureCode;

  @Column(name = "failure_message", length = 1000)
  private String failureMessage;

  @Column(name = "retryable_failure")
  private Boolean retryableFailure;

  @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
  private String requestFingerprint;

  @Column(name = "idempotency_key", length = 100, updatable = false)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "usage_type", nullable = false, updatable = false)
  private UsageType usageType;

  @Column(name = "usage_reservation_key", nullable = false, length = 200, updatable = false)
  private String usageReservationKey;

  @Column(name = "usage_compensated_at")
  private LocalDateTime usageCompensatedAt;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private LocalDateTime requestedAt;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "failed_at")
  private LocalDateTime failedAt;

  protected AiReport() {}

  public static AiReport requested(
      Long userId,
      Long assetId,
      UUID backtestJobId,
      ReportType type,
      String question,
      String promptVersion,
      String fingerprint,
      String idempotencyKey,
      UsageType usageType,
      String reservationKey) {
    AiReport value = new AiReport();
    value.id = UUID.randomUUID();
    value.userId = userId;
    value.assetId = assetId;
    value.backtestJobId = backtestJobId;
    value.reportType = type;
    value.question = trim(question);
    value.promptVersion = promptVersion;
    value.requestFingerprint = fingerprint;
    value.idempotencyKey = trim(idempotencyKey);
    value.usageType = usageType;
    value.usageReservationKey = reservationKey;
    value.status = ReportStatus.REQUESTED;
    value.currentStep =
        type == ReportType.RISK_ANALYSIS ? ReportStep.ASSET_RESOLUTION : ReportStep.RISK_DATA;
    value.requestedAt = LocalDateTime.now();
    return value;
  }

  public boolean startOrResume() {
    if (status == ReportStatus.COMPLETED || status == ReportStatus.FAILED) return false;
    if (status == ReportStatus.REQUESTED) {
      status = ReportStatus.RUNNING;
      startedAt = LocalDateTime.now();
    }
    attemptCount++;
    return true;
  }

  public void advance(ReportStep step) {
    if (status != ReportStatus.RUNNING) throw new IllegalStateException("Report is not running.");
    currentStep = step;
  }

  public void complete(
      String title,
      String content,
      String structuredResult,
      String model,
      long inputTokens,
      long outputTokens) {
    if (status != ReportStatus.RUNNING) throw new IllegalStateException("Report is not running.");
    this.title = title;
    this.content = content;
    this.structuredResult = structuredResult;
    this.model = model;
    this.inputTokenCount += inputTokens;
    this.outputTokenCount += outputTokens;
    status = ReportStatus.COMPLETED;
    currentStep = ReportStep.COMPLETED;
    completedAt = LocalDateTime.now();
    failureCode = null;
    failureMessage = null;
    retryableFailure = null;
  }

  public void addUsage(long inputTokens, long outputTokens, String model) {
    this.inputTokenCount += Math.max(0, inputTokens);
    this.outputTokenCount += Math.max(0, outputTokens);
    if (model != null && !model.isBlank()) this.model = model;
  }

  public void fail(String code, String message, boolean retryable) {
    if (status == ReportStatus.COMPLETED) return;
    status = ReportStatus.FAILED;
    failureCode = limit(code, 100);
    failureMessage = limit(message, 1000);
    retryableFailure = retryable;
    failedAt = LocalDateTime.now();
  }

  public boolean markUsageCompensated() {
    if (usageCompensatedAt != null) return false;
    usageCompensatedAt = LocalDateTime.now();
    return true;
  }

  private static String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String limit(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }

  public UUID getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public UUID getBacktestJobId() {
    return backtestJobId;
  }

  public ReportType getReportType() {
    return reportType;
  }

  public ReportStatus getStatus() {
    return status;
  }

  public ReportStep getCurrentStep() {
    return currentStep;
  }

  public String getQuestion() {
    return question;
  }

  public String getTitle() {
    return title;
  }

  public String getContent() {
    return content;
  }

  public String getStructuredResult() {
    return structuredResult;
  }

  public String getModel() {
    return model;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public long getInputTokenCount() {
    return inputTokenCount;
  }

  public long getOutputTokenCount() {
    return outputTokenCount;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public Boolean getRetryableFailure() {
    return retryableFailure;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public UsageType getUsageType() {
    return usageType;
  }

  public String getUsageReservationKey() {
    return usageReservationKey;
  }

  public LocalDateTime getUsageCompensatedAt() {
    return usageCompensatedAt;
  }

  public LocalDateTime getRequestedAt() {
    return requestedAt;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public LocalDateTime getFailedAt() {
    return failedAt;
  }
}
