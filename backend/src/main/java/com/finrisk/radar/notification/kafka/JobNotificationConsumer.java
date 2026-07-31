package com.finrisk.radar.notification.kafka;

import com.finrisk.radar.backtest.event.*;
import com.finrisk.radar.backtest.kafka.BacktestTopics;
import com.finrisk.radar.notification.*;
import com.finrisk.radar.notification.NotificationService.Command;
import com.finrisk.radar.report.event.*;
import com.finrisk.radar.report.kafka.ReportTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class JobNotificationConsumer {
  private final NotificationService notifications;

  public JobNotificationConsumer(NotificationService notifications) {
    this.notifications = notifications;
  }

  @KafkaListener(
      topics = BacktestTopics.COMPLETED,
      groupId = NotificationKafkaConfiguration.GROUP_ID,
      containerFactory = "notificationKafkaListenerContainerFactory")
  public void backtestCompleted(BacktestCompletedEvent event) {
    String id = event.jobId().toString();
    notifications.create(
        event.userId(),
        new Command(
            "backtest:completed:" + id,
            NotificationType.BACKTEST_COMPLETED,
            "Backtest completed",
            "Your backtest has completed.",
            NotificationReferenceType.BACKTEST,
            id,
            "/backtests?jobId=" + id));
  }

  @KafkaListener(
      topics = BacktestTopics.FAILED,
      groupId = NotificationKafkaConfiguration.GROUP_ID,
      containerFactory = "notificationKafkaListenerContainerFactory")
  public void backtestFailed(BacktestFailedEvent event) {
    String id = event.jobId().toString();
    notifications.create(
        event.userId(),
        new Command(
            "backtest:failed:" + id,
            NotificationType.BACKTEST_FAILED,
            "Backtest failed",
            "Your backtest could not be completed.",
            NotificationReferenceType.BACKTEST,
            id,
            "/backtests?jobId=" + id));
  }

  @KafkaListener(
      topics = ReportTopics.COMPLETED,
      groupId = NotificationKafkaConfiguration.GROUP_ID,
      containerFactory = "notificationKafkaListenerContainerFactory")
  public void reportCompleted(ReportCompletedEvent event) {
    String id = event.reportId().toString();
    notifications.create(
        event.userId(),
        new Command(
            "report:completed:" + id,
            NotificationType.REPORT_COMPLETED,
            "AI report completed",
            "Your AI report is ready.",
            NotificationReferenceType.AI_REPORT,
            id,
            "/reports/" + id));
  }

  @KafkaListener(
      topics = ReportTopics.FAILED,
      groupId = NotificationKafkaConfiguration.GROUP_ID,
      containerFactory = "notificationKafkaListenerContainerFactory")
  public void reportFailed(ReportFailedEvent event) {
    String id = event.reportId().toString();
    notifications.create(
        event.userId(),
        new Command(
            "report:failed:" + id,
            NotificationType.REPORT_FAILED,
            "AI report failed",
            "Your AI report could not be completed.",
            NotificationReferenceType.AI_REPORT,
            id,
            "/reports/" + id));
  }
}
