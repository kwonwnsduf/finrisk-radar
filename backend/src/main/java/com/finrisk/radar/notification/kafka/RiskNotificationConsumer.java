package com.finrisk.radar.notification.kafka;

import com.finrisk.radar.notification.*;
import com.finrisk.radar.notification.NotificationService.Command;
import com.finrisk.radar.risk.RiskSeverity;
import com.finrisk.radar.risk.event.RiskScoreCalculatedEvent;
import com.finrisk.radar.risk.kafka.RiskTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RiskNotificationConsumer {
  private final NotificationService notifications;

  public RiskNotificationConsumer(NotificationService notifications) {
    this.notifications = notifications;
  }

  @KafkaListener(
      topics = RiskTopics.CALCULATED,
      groupId = NotificationKafkaConfiguration.GROUP_ID,
      containerFactory = "notificationKafkaListenerContainerFactory")
  public void calculated(RiskScoreCalculatedEvent event) {
    if (event.highRiskSignalCount() <= 0
        || (event.highestSeverity() != RiskSeverity.HIGH
            && event.highestSeverity() != RiskSeverity.CRITICAL)) return;
    String jobId = event.jobId().toString();
    String assetId = event.assetId().toString();
    notifications.create(
        event.userId(),
        new Command(
            "risk:calculated:" + jobId,
            NotificationType.HIGH_RISK_SIGNAL_DETECTED,
            "Important risk signal detected",
            event.highRiskSignalCount() + " high-priority risk signal(s) require attention.",
            NotificationReferenceType.ASSET,
            assetId,
            "/assets/" + assetId));
  }
}
