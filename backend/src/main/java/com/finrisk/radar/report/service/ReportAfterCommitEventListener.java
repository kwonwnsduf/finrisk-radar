package com.finrisk.radar.report.service;

import com.finrisk.radar.report.ReportStatus;
import com.finrisk.radar.report.event.*;
import com.finrisk.radar.report.kafka.ReportEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class ReportAfterCommitEventListener {
  private final ReportEventPublisher publisher;

  public ReportAfterCommitEventListener(ReportEventPublisher publisher) {
    this.publisher = publisher;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void finished(ReportFinishedNotification event) {
    if (event.status() == ReportStatus.COMPLETED) {
      publisher.completed(
          new ReportCompletedEvent(
              event.reportId(), event.userId(), event.reportType(), event.occurredAt()));
    } else if (event.status() == ReportStatus.FAILED) {
      publisher.failed(
          new ReportFailedEvent(
              event.reportId(), event.userId(), event.reportType(), event.occurredAt()));
    }
  }
}
