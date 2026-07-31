package com.finrisk.radar.notification.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.finrisk.radar.notification.*;
import com.finrisk.radar.payment.outbox.PaymentDomainEvent;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class PaymentNotificationConsumerTest {
  private final NotificationService notifications = mock(NotificationService.class);
  private final PaymentNotificationConsumer consumer =
      new PaymentNotificationConsumer(notifications);

  @Test
  void consumesTheCommonEnvelopeForCompletedPayments() {
    UUID id = UUID.randomUUID();
    consumer.payment(event(id, "PaymentCompletedEvent"));

    verify(notifications)
        .create(
            eq(7L),
            argThat(
                command ->
                    command.eventId().equals("payment:" + id)
                        && command.type() == NotificationType.PAYMENT_COMPLETED));
  }

  @Test
  void ignoresUnrelatedPaymentDomainEvents() {
    consumer.payment(event(UUID.randomUUID(), "SubscriptionActivatedEvent"));
    verifyNoInteractions(notifications);
  }

  @Test
  void sendsRecoveryRequiredOnlyToAdmins() {
    consumer.payment(event(UUID.randomUUID(), "PaymentRecoveryRequiredEvent"));
    verify(notifications)
        .createForAdmins(
            argThat(command -> command.type() == NotificationType.PAYMENT_RECOVERY_REQUIRED));
    verify(notifications, never()).create(anyLong(), any());
  }

  private PaymentDomainEvent event(UUID id, String type) {
    return new PaymentDomainEvent(
        id,
        type,
        LocalDateTime.now(),
        Map.of("userId", 7L, "orderId", "fr_public_order"),
        1);
  }
}
