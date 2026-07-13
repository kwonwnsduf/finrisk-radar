package com.finrisk.radar.risk;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "risk_calculation_jobs")
public class RiskCalculationJob extends BaseTimeEntity {
  @Id
  @Column(name = "job_id")
  private UUID jobId;

  @Column(name = "requested_by_user_id", nullable = false)
  private Long userId;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RiskCalculationStatus status;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "failed_at")
  private LocalDateTime failedAt;

  @Column(name = "failure_code")
  private String failureCode;

  @Column(name = "failure_message")
  private String failureMessage;

  @Column(name = "rule_version", nullable = false)
  private String ruleVersion;

  @Column(name = "data_as_of_date", nullable = false)
  private LocalDate dataAsOfDate;

  protected RiskCalculationJob() {}

  public static RiskCalculationJob requested(
      Long user, Long asset, String version, LocalDate date) {
    RiskCalculationJob j = new RiskCalculationJob();
    j.jobId = UUID.randomUUID();
    j.userId = user;
    j.assetId = asset;
    j.status = RiskCalculationStatus.REQUESTED;
    j.requestedAt = LocalDateTime.now();
    j.ruleVersion = version;
    j.dataAsOfDate = date;
    return j;
  }

  public void complete() {
    status = RiskCalculationStatus.COMPLETED;
    completedAt = LocalDateTime.now();
  }

  public void fail(String code, String message) {
    if (status == RiskCalculationStatus.COMPLETED) return;
    status = RiskCalculationStatus.FAILED;
    failedAt = LocalDateTime.now();
    failureCode = code;
    failureMessage =
        message == null ? null : message.substring(0, Math.min(1000, message.length()));
  }

  public UUID getJobId() {
    return jobId;
  }

  public Long getUserId() {
    return userId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public RiskCalculationStatus getStatus() {
    return status;
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

  public String getFailureCode() {
    return failureCode;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public String getRuleVersion() {
    return ruleVersion;
  }

  public LocalDate getDataAsOfDate() {
    return dataAsOfDate;
  }
}
