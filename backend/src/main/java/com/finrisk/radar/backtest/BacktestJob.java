package com.finrisk.radar.backtest;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "backtest_jobs")
public class BacktestJob extends BaseTimeEntity {
  @Id
  @Column(name = "job_id", nullable = false, updatable = false)
  private UUID jobId;

  @Column(name = "requested_by_user_id", nullable = false, updatable = false)
  private Long requestedByUserId;

  @Column(name = "asset_id", nullable = false, updatable = false)
  private Long assetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "strategy_type", nullable = false, length = 30, updatable = false)
  private StrategyType strategyType;

  @Column(name = "start_date", nullable = false, updatable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false, updatable = false)
  private LocalDate endDate;

  @Column(name = "initial_cash", nullable = false, precision = 20, scale = 6, updatable = false)
  private BigDecimal initialCash;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "strategy_config", columnDefinition = "jsonb", updatable = false)
  private String strategyConfig;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BacktestStatus status;

  @Column(length = 1000)
  private String message;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "natural_language_question", length = 500)
  private String naturalLanguageQuestion;

  @Column(name = "parser_model", length = 100)
  private String parserModel;

  @Column(name = "parser_prompt_version", length = 100)
  private String parserPromptVersion;

  @Column(name = "parser_input_token_count")
  private Long parserInputTokenCount;

  @Column(name = "parser_output_token_count")
  private Long parserOutputTokenCount;

  protected BacktestJob() {}

  private BacktestJob(
      UUID jobId,
      Long userId,
      Long assetId,
      StrategyType strategyType,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal initialCash,
      String strategyConfig) {
    this.jobId = jobId;
    this.requestedByUserId = userId;
    this.assetId = assetId;
    this.strategyType = strategyType;
    this.startDate = startDate;
    this.endDate = endDate;
    this.initialCash = initialCash;
    this.strategyConfig = strategyConfig;
    this.status = BacktestStatus.REQUESTED;
    this.message = "Backtest requested.";
  }

  public static BacktestJob requested(
      Long userId,
      Long assetId,
      StrategyType strategyType,
      LocalDate startDate,
      LocalDate endDate) {
    return requested(
        userId, assetId, strategyType, startDate, endDate, new BigDecimal("10000000.000000"), null);
  }

  public static BacktestJob requested(
      Long userId,
      Long assetId,
      StrategyType strategyType,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal initialCash,
      String strategyConfig) {
    return new BacktestJob(
        UUID.randomUUID(),
        userId,
        assetId,
        strategyType,
        startDate,
        endDate,
        initialCash,
        strategyConfig);
  }

  public boolean start() {
    if (status != BacktestStatus.REQUESTED) return false;
    status = BacktestStatus.RUNNING;
    startedAt = LocalDateTime.now();
    message = "Backtest is running.";
    return true;
  }

  public void complete() {
    if (status != BacktestStatus.RUNNING)
      throw new IllegalStateException("Backtest is not running.");
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

  public void attachNaturalLanguageMetadata(
      String question, String model, String promptVersion, long inputTokens, long outputTokens) {
    this.naturalLanguageQuestion = question;
    this.parserModel = model;
    this.parserPromptVersion = promptVersion;
    this.parserInputTokenCount = inputTokens;
    this.parserOutputTokenCount = outputTokens;
  }

  public UUID getJobId() {
    return jobId;
  }

  public Long getRequestedByUserId() {
    return requestedByUserId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public StrategyType getStrategyType() {
    return strategyType;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public BigDecimal getInitialCash() {
    return initialCash;
  }

  public String getStrategyConfig() {
    return strategyConfig;
  }

  public BacktestStatus getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }
}
