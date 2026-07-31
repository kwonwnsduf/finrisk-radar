package com.finrisk.radar.notification;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public final class NotificationModels {
  private NotificationModels() {}

  public record Item(
      Long id,
      NotificationType type,
      String title,
      String message,
      NotificationReferenceType referenceType,
      String referenceId,
      String targetUrl,
      boolean isRead,
      LocalDateTime createdAt,
      LocalDateTime readAt) {
    static Item from(Notification value) {
      return new Item(
          value.getId(),
          value.getType(),
          value.getTitle(),
          value.getMessage(),
          value.getReferenceType(),
          value.getReferenceId(),
          value.getTargetUrl(),
          value.isRead(),
          value.getCreatedAt(),
          value.getReadAt());
    }
  }

  public record PageResponse(
      List<Item> items, int page, int size, long totalElements, int totalPages) {
    static PageResponse from(Page<Notification> values) {
      return new PageResponse(
          values.getContent().stream().map(Item::from).toList(),
          values.getNumber(),
          values.getSize(),
          values.getTotalElements(),
          values.getTotalPages());
    }
  }

  public record UnreadCount(long count) {}

  public record ReadAllResult(int updatedCount) {}
}
