package com.finrisk.radar.notification.kafka;

import com.finrisk.radar.fsd.*;
import com.finrisk.radar.fsd.event.FsdReviewRequiredEvent;
import com.finrisk.radar.notification.*;
import com.finrisk.radar.notification.NotificationService.Command;
import com.finrisk.radar.payment.outbox.*;
import java.util.Map;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentNotificationConsumer {
  private final NotificationService notifications;

  public PaymentNotificationConsumer(NotificationService notifications) {
    this.notifications = notifications;
  }

  @KafkaListener(
      topics = PaymentEventConfiguration.TOPIC,
      groupId = NotificationKafkaConfiguration.GROUP_ID,
      containerFactory = "notificationKafkaListenerContainerFactory")
  public void payment(PaymentDomainEvent event) {
    switch (event.eventType()) {
      case "PaymentCompletedEvent", "PaymentRecoveryCompletedEvent" ->
          createForUser(
              event,
              NotificationType.PAYMENT_COMPLETED,
              "Payment completed",
              "Your payment has been completed.");
      case "PaymentCanceledEvent" ->
          createForUser(
              event,
              NotificationType.PAYMENT_CANCELED,
              "Payment canceled",
              "Your payment cancellation has been completed.");
      case "PaymentFailedEvent" ->
          createForUser(
              event,
              NotificationType.PAYMENT_FAILED,
              "Payment failed",
              "Your payment could not be completed.");
      case "PaymentRecoveryRequiredEvent" -> createRecoveryForAdmins(event);
      default -> {
        // Other payment-domain events are intentionally consumed without a notification.
      }
    }
  }

  @KafkaListener(
      topics = FsdTopics.REVIEW_REQUIRED,
      groupId = NotificationKafkaConfiguration.GROUP_ID,
      containerFactory = "notificationKafkaListenerContainerFactory")
  public void fsd(FsdReviewRequiredEvent event) {
    if (event.decision() != FsdDecision.REVIEW && event.decision() != FsdDecision.BLOCK) return;
    String id = event.fsdEventId().toString();
    notifications.createForAdmins(
        new Command(
            "fsd:review-required:" + id,
            NotificationType.FSD_REVIEW_REQUIRED,
            "FSD review required",
            "A new fraud-screening event requires an operator review.",
            NotificationReferenceType.FSD_EVENT,
            id,
            "/admin/fsd"));
  }

  private void createForUser(
      PaymentDomainEvent event, NotificationType type, String title, String message) {
    Long userId = requiredLong(event.payload(), "userId");
    String orderId = requiredString(event.payload(), "orderId");
    notifications.create(
        userId,
        new Command(
            "payment:" + event.eventId(),
            type,
            title,
            message,
            NotificationReferenceType.PAYMENT_ORDER,
            orderId,
            "/payments"));
  }

  private void createRecoveryForAdmins(PaymentDomainEvent event) {
    String orderId = requiredString(event.payload(), "orderId");
    notifications.createForAdmins(
        new Command(
            "payment:" + event.eventId(),
            NotificationType.PAYMENT_RECOVERY_REQUIRED,
            "Payment recovery required",
            "A payment requires operator reconciliation.",
            NotificationReferenceType.PAYMENT_ORDER,
            orderId,
            "/admin/payments"));
  }

  private Long requiredLong(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    if (value instanceof Number number) return number.longValue();
    throw new IllegalArgumentException("Payment event is missing " + key + ".");
  }

  private String requiredString(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    if (value instanceof String text && !text.isBlank()) return text;
    throw new IllegalArgumentException("Payment event is missing " + key + ".");
  }
}
