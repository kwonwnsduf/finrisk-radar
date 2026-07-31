package com.finrisk.radar.backtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.backtest.engine.BacktestCalculationResult;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BacktestJobService {
  private final BacktestJobRepository jobs;
  private final BacktestResultRepository results;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher events;

  public BacktestJobService(
      BacktestJobRepository jobs,
      BacktestResultRepository results,
      ObjectMapper objectMapper,
      ApplicationEventPublisher events) {
    this.jobs = jobs;
    this.results = results;
    this.objectMapper = objectMapper;
    this.events = events;
  }

  @Transactional
  public BacktestJob createRequested(
      Long userId,
      Long assetId,
      StrategyType strategyType,
      LocalDate startDate,
      LocalDate endDate) {
    return jobs.save(BacktestJob.requested(userId, assetId, strategyType, startDate, endDate));
  }

  @Transactional
  public void attachNaturalLanguageMetadata(
      UUID jobId,
      String question,
      String model,
      String version,
      long inputTokens,
      long outputTokens) {
    BacktestJob job =
        jobs.findById(jobId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BACKTEST_JOB_NOT_FOUND));

    job.attachNaturalLanguageMetadata(question, model, version, inputTokens, outputTokens);
  }

  @Transactional
  public BacktestJob createRequested(
      Long userId,
      Long assetId,
      StrategyType strategyType,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal initialCash,
      String strategyConfig) {
    return jobs.save(
        BacktestJob.requested(
            userId, assetId, strategyType, startDate, endDate, initialCash, strategyConfig));
  }

  @Transactional
  public boolean markRunning(UUID jobId) {
    return find(jobId).start();
  }

  @Transactional
  public void completeWithResult(UUID jobId, BacktestCalculationResult calculation) {
    BacktestJob job = find(jobId);
    if (job.getStatus() != BacktestStatus.RUNNING)
      throw new IllegalStateException("Backtest is not running.");
    results.save(
        BacktestResult.from(
            jobId,
            calculation,
            toJson(calculation.monthlyReturns()),
            toJson(calculation.dailyPortfolioValues()),
            toJson(calculation.trades())));
    job.complete();
    events.publishEvent(
        new com.finrisk.radar.backtest.service.BacktestFinishedNotification(
            job.getJobId(),
            job.getRequestedByUserId(),
            job.getAssetId(),
            job.getStatus(),
            Instant.now()));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(UUID jobId, String message) {
    String safe = message == null || message.isBlank() ? "Backtest failed." : message;
    BacktestJob job = find(jobId);
    if (job.getStatus() == BacktestStatus.COMPLETED || job.getStatus() == BacktestStatus.FAILED)
      return;
    job.fail(safe.substring(0, Math.min(safe.length(), 1000)));
    events.publishEvent(
        new com.finrisk.radar.backtest.service.BacktestFinishedNotification(
            job.getJobId(),
            job.getRequestedByUserId(),
            job.getAssetId(),
            job.getStatus(),
            Instant.now()));
  }

  @Transactional(readOnly = true)
  public BacktestJob getInternal(UUID jobId) {
    return find(jobId);
  }

  private BacktestJob find(UUID jobId) {
    return jobs.findById(jobId)
        .orElseThrow(() -> new BusinessException(ErrorCode.BACKTEST_JOB_NOT_FOUND));
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
  }
}
