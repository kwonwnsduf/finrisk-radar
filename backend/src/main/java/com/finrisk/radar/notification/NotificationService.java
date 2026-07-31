package com.finrisk.radar.notification;

import static com.finrisk.radar.notification.NotificationModels.*;

import com.finrisk.radar.global.error.*;
import com.finrisk.radar.user.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
  private final NotificationRepository notifications;
  private final UserRepository users;
  private final MeterRegistry meters;

  public NotificationService(
      NotificationRepository notifications, UserRepository users, MeterRegistry meters) {
    this.notifications = notifications;
    this.users = users;
    this.meters = meters;
  }

  @Transactional
  public boolean create(Long userId, Command command) {
    validate(command);
    int inserted =
        notifications.insertIgnoringDuplicate(
            userId,
            command.type().name(),
            command.title(),
            command.message(),
            command.referenceType().name(),
            command.referenceId(),
            command.targetUrl(),
            command.eventId(),
            LocalDateTime.now());
    meters
        .counter(
            inserted == 1 ? "notification.created" : "notification.duplicate",
            "type",
            command.type().name())
        .increment();
    return inserted == 1;
  }

  @Transactional
  public int createForAdmins(Command command) {
    List<Long> adminIds = users.findIdsByRole(Role.ROLE_ADMIN);
    int created = 0;
    for (Long adminId : adminIds) if (create(adminId, command)) created++;
    return created;
  }

  @Transactional
  public Item markRead(Long userId, Long notificationId) {
    Notification notification =
        notifications
            .findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
    notification.markRead(LocalDateTime.now());
    return Item.from(notification);
  }

  @Transactional
  public ReadAllResult markAllRead(Long userId) {
    return new ReadAllResult(notifications.markAllRead(userId, LocalDateTime.now()));
  }

  private void validate(Command command) {
    if (command.eventId() == null
        || command.eventId().isBlank()
        || command.eventId().length() > 100
        || command.referenceId() == null
        || command.referenceId().isBlank()
        || command.targetUrl() == null
        || !command.targetUrl().startsWith("/")
        || command.targetUrl().startsWith("//")) {
      throw new IllegalArgumentException("Invalid notification command.");
    }
  }

  public record Command(
      String eventId,
      NotificationType type,
      String title,
      String message,
      NotificationReferenceType referenceType,
      String referenceId,
      String targetUrl) {}
}
