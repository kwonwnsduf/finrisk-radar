package com.finrisk.radar.notification.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.finrisk.radar.backtest.event.*;
import com.finrisk.radar.notification.*;
import com.finrisk.radar.report.ReportType;
import com.finrisk.radar.report.event.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobNotificationConsumerTest {
  private final NotificationService notifications = mock(NotificationService.class);
  private final JobNotificationConsumer consumer = new JobNotificationConsumer(notifications);

  @Test
  void mapsBacktestAndReportOutcomesToTheirOwners() {
    UUID backtestId = UUID.randomUUID();
    UUID reportId = UUID.randomUUID();

    consumer.backtestCompleted(new BacktestCompletedEvent(backtestId, 1L, 2L, Instant.now()));
    consumer.reportFailed(
        new ReportFailedEvent(reportId, 3L, ReportType.RISK_ANALYSIS, Instant.now()));

    verify(notifications)
        .create(
            eq(1L),
            argThat(
                command ->
                    command.eventId().equals("backtest:completed:" + backtestId)
                        && command.targetUrl().equals("/backtests?jobId=" + backtestId)));
    verify(notifications)
        .create(
            eq(3L),
            argThat(
                command ->
                    command.eventId().equals("report:failed:" + reportId)
                        && command.targetUrl().equals("/reports/" + reportId)));
  }
}
