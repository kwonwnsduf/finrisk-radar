package com.finrisk.radar.notification.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.finrisk.radar.notification.*;
import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.event.RiskScoreCalculatedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskNotificationConsumerTest {
  private final NotificationService notifications = mock(NotificationService.class);
  private final RiskNotificationConsumer consumer = new RiskNotificationConsumer(notifications);

  @Test
  void notifiesOnlyTheRequestingUserForCurrentHighRiskSummary() {
    UUID jobId = UUID.randomUUID();
    consumer.calculated(event(jobId, RiskSeverity.CRITICAL, 2));

    verify(notifications)
        .create(
            eq(31L),
            argThat(
                command ->
                    command.eventId().equals("risk:calculated:" + jobId)
                        && command.type() == NotificationType.HIGH_RISK_SIGNAL_DETECTED));
  }

  @Test
  void ignoresLowAndMediumOnlyCalculations() {
    consumer.calculated(event(UUID.randomUUID(), RiskSeverity.MEDIUM, 0));
    verifyNoInteractions(notifications);
  }

  private RiskScoreCalculatedEvent event(UUID id, RiskSeverity severity, long highCount) {
    return new RiskScoreCalculatedEvent(
        id,
        31L,
        9L,
        100L,
        72,
        RiskGrade.HIGH,
        DefaultStatus.NONE,
        severity,
        highCount,
        Instant.now());
  }
}
