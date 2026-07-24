package com.finrisk.radar.report.service;

import com.finrisk.radar.global.error.*;
import com.finrisk.radar.report.*;
import com.finrisk.radar.report.llm.ReportPromptFactory;
import com.finrisk.radar.usage.*;
import com.finrisk.radar.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ReportPersistenceService {
  private final AiReportRepository reports;
  private final UserRepository users;
  private final MeterRegistry meters;
  private final ReportPromptFactory prompts;

  public ReportPersistenceService(
      AiReportRepository reports,
      UserRepository users,
      MeterRegistry meters,
      ReportPromptFactory prompts) {
    this.reports = reports;
    this.users = users;
    this.meters = meters;
    this.prompts = prompts;
  }

  @Transactional
  public Creation create(
      Long userId,
      Long assetId,
      UUID backtestJobId,
      ReportType type,
      String question,
      String fingerprint,
      String idempotencyKey,
      UsageType usageType,
      UsageLimitService.UsageReservation reservation) {
    users
        .findByIdForUpdate(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      var existing = reports.findByUserIdAndIdempotencyKey(userId, idempotencyKey.trim());
      if (existing.isPresent()) return new Creation(existing.get(), true);
    }
    var duplicate =
        reports
            .findFirstByUserIdAndReportTypeAndRequestFingerprintAndRequestedAtAfterOrderByRequestedAtDesc(
                userId, type, fingerprint, LocalDateTime.now().minusMinutes(5));
    if (duplicate.isPresent()) return new Creation(duplicate.get(), true);
    if (reports.countByUserIdAndStatusIn(
            userId, List.of(ReportStatus.REQUESTED, ReportStatus.RUNNING))
        >= 1) throw new BusinessException(ErrorCode.REPORT_CONCURRENCY_LIMIT);
    AiReport report =
        AiReport.requested(
            userId,
            assetId,
            backtestJobId,
            type,
            question,
            prompts.version(type),
            fingerprint,
            idempotencyKey,
            usageType,
            reservation.key());
    try {
      AiReport saved = reports.saveAndFlush(report);
      meters.counter("ai.agent.requests", "reportType", type.name()).increment();
      return new Creation(saved, false);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
  }

  @Transactional
  public boolean begin(UUID id) {
    AiReport r = locked(id);
    return r.startOrResume();
  }

  @Transactional
  public void advance(UUID id, ReportStep step) {
    locked(id).advance(step);
  }

  @Transactional
  public void addUsage(UUID id, long input, long output, String model) {
    locked(id).addUsage(input, output, model);
  }

  @Transactional
  public void complete(UUID id, GeneratedReport generated) {
    AiReport r = locked(id);
    r.advance(ReportStep.REPORT_SAVE);
    r.complete(
        generated.title(),
        generated.summary(),
        generated.json(),
        generated.llm().model(),
        generated.llm().usage().inputTokens(),
        generated.llm().usage().outputTokens());
    meters.counter("report.generated", "reportType", r.getReportType().name()).increment();
  }

  @Transactional
  public void fail(UUID id, String code, String message, boolean retryable) {
    AiReport r = locked(id);
    r.fail(code, message, retryable);
    meters.counter("report.failed", "reportType", r.getReportType().name()).increment();
  }

  @Transactional
  public Optional<String> failAndMarkCompensation(UUID id, String code, String message) {
    AiReport r = locked(id);
    r.fail(code, message, false);
    meters.counter("report.failed", "reportType", r.getReportType().name()).increment();
    return r.markUsageCompensated() ? Optional.of(r.getUsageReservationKey()) : Optional.empty();
  }

  @Transactional(readOnly = true)
  public AiReport get(UUID id) {
    return reports
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
  }

  private AiReport locked(UUID id) {
    return reports
        .findByIdForUpdate(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
  }

  public record Creation(AiReport report, boolean reused) {}
}
