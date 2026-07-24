package com.finrisk.radar.report.api;

import com.fasterxml.jackson.databind.*;
import com.finrisk.radar.report.*;
import java.time.*;
import java.util.UUID;

public record ReportResponse(
    UUID id,
    Long assetId,
    UUID backtestJobId,
    ReportType reportType,
    ReportStatus status,
    ReportStep currentStep,
    String question,
    String title,
    String content,
    JsonNode structuredResult,
    String model,
    String promptVersion,
    long inputTokenCount,
    long outputTokenCount,
    String failureCode,
    String failureMessage,
    LocalDateTime requestedAt,
    LocalDateTime completedAt) {
  public static ReportResponse from(AiReport r, ObjectMapper mapper) {
    try {
      return new ReportResponse(
          r.getId(),
          r.getAssetId(),
          r.getBacktestJobId(),
          r.getReportType(),
          r.getStatus(),
          r.getCurrentStep(),
          r.getQuestion(),
          r.getTitle(),
          r.getContent(),
          r.getStructuredResult() == null ? null : mapper.readTree(r.getStructuredResult()),
          r.getModel(),
          r.getPromptVersion(),
          r.getInputTokenCount(),
          r.getOutputTokenCount(),
          r.getFailureCode(),
          r.getFailureMessage(),
          r.getRequestedAt(),
          r.getCompletedAt());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
