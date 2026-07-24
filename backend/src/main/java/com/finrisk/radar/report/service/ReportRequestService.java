package com.finrisk.radar.report.service;

import com.finrisk.radar.global.error.*;
import com.finrisk.radar.report.*;
import com.finrisk.radar.report.event.ReportGenerationRequestedEvent;
import com.finrisk.radar.report.kafka.*;
import com.finrisk.radar.report.llm.LlmClient;
import com.finrisk.radar.report.tool.AssetSearchTool;
import com.finrisk.radar.usage.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ReportRequestService {
  private final LlmClient llm;
  private final UsageLimitService usage;
  private final ReportPersistenceService persistence;
  private final ReportEventPublisher publisher;
  private final AssetSearchTool assets;

  public ReportRequestService(
      LlmClient llm,
      UsageLimitService usage,
      ReportPersistenceService persistence,
      ReportEventPublisher publisher,
      AssetSearchTool assets) {
    this.llm = llm;
    this.usage = usage;
    this.persistence = persistence;
    this.publisher = publisher;
    this.assets = assets;
  }

  public ReportPersistenceService.Creation request(
      Long userId,
      Long assetId,
      UUID backtestId,
      ReportType type,
      String question,
      String idempotencyKey) {
    if (!llm.configured()) throw new BusinessException(ErrorCode.REPORT_LLM_NOT_CONFIGURED);
    if (type == ReportType.RISK_ANALYSIS && assetId == null)
      assetId = assets.resolve(null, question).getId();
    UsageType usageType =
        type == ReportType.RISK_ANALYSIS ? UsageType.RISK_REPORT : UsageType.AI_AGENT;
    UsageLimitService.UsageReservation reservation = usage.reserve(userId, usageType);
    ReportPersistenceService.Creation creation;
    try {
      creation =
          persistence.create(
              userId,
              assetId,
              backtestId,
              type,
              question,
              fingerprint(type, assetId, backtestId, question),
              idempotencyKey,
              usageType,
              reservation);
    } catch (RuntimeException exception) {
      usage.release(reservation);
      throw exception;
    }
    if (creation.reused()) {
      usage.release(reservation);
      return creation;
    }
    AiReport report = creation.report();
    try {
      publisher.requested(
          new ReportGenerationRequestedEvent(
              report.getId(), userId, type, report.getPromptVersion(), Instant.now()));
    } catch (ReportEventPublishException exception) {
      persistence
          .failAndMarkCompensation(
              report.getId(),
              ErrorCode.REPORT_EVENT_PUBLISH_FAILED.getCode(),
              ErrorCode.REPORT_EVENT_PUBLISH_FAILED.getMessage())
          .ifPresent(usage::releaseKey);
      throw new BusinessException(ErrorCode.REPORT_EVENT_PUBLISH_FAILED);
    }
    return creation;
  }

  private String fingerprint(ReportType type, Long assetId, UUID backtestId, String question) {
    String input =
        type
            + "|"
            + Objects.toString(assetId, "")
            + "|"
            + Objects.toString(backtestId, "")
            + "|"
            + (question == null ? "" : question.trim().toLowerCase(Locale.ROOT));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
