package com.finrisk.radar.notification;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.notification.NotificationModels.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationQueryService queries;
  private final NotificationService notifications;

  public NotificationController(
      NotificationQueryService queries, NotificationService notifications) {
    this.queries = queries;
    this.notifications = notifications;
  }

  @GetMapping
  public ApiResponse<PageResponse> list(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestParam(required = false) Boolean read,
      @RequestParam(required = false) NotificationType type,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(queries.list(principal.userId(), read, type, page, size));
  }

  @GetMapping("/unread-count")
  public ApiResponse<UnreadCount> unreadCount(
      @AuthenticationPrincipal CustomUserPrincipal principal) {
    return ApiResponse.success(queries.unreadCount(principal.userId()));
  }

  @PatchMapping("/{notificationId}/read")
  public ApiResponse<Item> read(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @PathVariable Long notificationId) {
    return ApiResponse.success(notifications.markRead(principal.userId(), notificationId));
  }

  @PatchMapping("/read-all")
  public ApiResponse<ReadAllResult> readAll(
      @AuthenticationPrincipal CustomUserPrincipal principal) {
    return ApiResponse.success(notifications.markAllRead(principal.userId()));
  }
}
