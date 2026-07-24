package com.finrisk.radar.report.service;

import com.finrisk.radar.report.*;
import com.finrisk.radar.report.event.ReportGenerationRequestedEvent;
import com.finrisk.radar.report.kafka.*;
import com.finrisk.radar.usage.UsageLimitService;
import java.time.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportDispatchRecoveryScheduler {
  private final AiReportRepository reports;
  private final ReportEventPublisher publisher;
  private final ReportPersistenceService persistence;
  private final UsageLimitService usage;

  public ReportDispatchRecoveryScheduler(
      AiReportRepository reports,
      ReportEventPublisher publisher,
      ReportPersistenceService persistence,
      UsageLimitService usage) {
    this.reports = reports;
    this.publisher = publisher;
    this.persistence = persistence;
    this.usage = usage;
  }

  @Scheduled(fixedDelayString = "${app.llm.report-recovery-delay:60000}")
  public void recover() {
    for (AiReport report :
        reports.findTop50ByStatusAndRequestedAtBeforeOrderByRequestedAtAsc(
            ReportStatus.REQUESTED, LocalDateTime.now().minusMinutes(1))) {
      publish(report, true);
    }
    for (AiReport report :
        reports.findTop50ByStatusAndStartedAtBeforeOrderByStartedAtAsc(
            ReportStatus.RUNNING, LocalDateTime.now().minusMinutes(5))) {
      publish(report, false);
    }
  }

  private void publish(AiReport report, boolean compensateOnFailure) {
    try {
      publisher.requested(
          new ReportGenerationRequestedEvent(
              report.getId(),
              report.getUserId(),
              report.getReportType(),
              report.getPromptVersion(),
              Instant.now()));
    } catch (ReportEventPublishException exception) {
      if (compensateOnFailure) {
        persistence
            .failAndMarkCompensation(
                report.getId(),
                "REPORT_EVENT_PUBLISH_FAILED",
                "Report event could not be recovered.")
            .ifPresent(usage::releaseKey);
      } else {
        // Kafka had already accepted this report, so a final recovery failure must preserve usage.
        persistence.fail(
            report.getId(),
            "REPORT_STALE_RECOVERY_PUBLISH_FAILED",
            "Stale report recovery event could not be published.",
            true);
      }
    }
  }
}
