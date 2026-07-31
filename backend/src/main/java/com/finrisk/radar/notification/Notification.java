package com.finrisk.radar.notification;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private NotificationType type;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 500)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(name = "reference_type", nullable = false, length = 40)
  private NotificationReferenceType referenceType;

  @Column(name = "reference_id", nullable = false, length = 100)
  private String referenceId;

  @Column(name = "target_url", length = 500)
  private String targetUrl;

  @Column(name = "event_id", nullable = false, length = 100)
  private String eventId;

  @Column(name = "is_read", nullable = false)
  private boolean read;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "read_at")
  private LocalDateTime readAt;

  protected Notification() {}

  public void markRead(LocalDateTime now) {
    if (read) return;
    read = true;
    readAt = now.truncatedTo(ChronoUnit.MICROS);
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public NotificationType getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getMessage() {
    return message;
  }

  public NotificationReferenceType getReferenceType() {
    return referenceType;
  }

  public String getReferenceId() {
    return referenceId;
  }

  public String getTargetUrl() {
    return targetUrl;
  }

  public String getEventId() {
    return eventId;
  }

  public boolean isRead() {
    return read;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getReadAt() {
    return readAt;
  }
}
