package com.finrisk.radar.notification;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository
    extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

  Optional<Notification> findByIdAndUserId(Long id, Long userId);

  long countByUserIdAndReadFalse(Long userId);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO notifications(
              user_id, type, title, message, reference_type, reference_id,
              target_url, event_id, is_read, created_at
          ) VALUES (
              :userId, :type, :title, :message, :referenceType, :referenceId,
              :targetUrl, :eventId, FALSE, :createdAt
          )
          ON CONFLICT (event_id, user_id) DO NOTHING
          """,
      nativeQuery = true)
  int insertIgnoringDuplicate(
      @Param("userId") Long userId,
      @Param("type") String type,
      @Param("title") String title,
      @Param("message") String message,
      @Param("referenceType") String referenceType,
      @Param("referenceId") String referenceId,
      @Param("targetUrl") String targetUrl,
      @Param("eventId") String eventId,
      @Param("createdAt") LocalDateTime createdAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update Notification n set n.read = true, n.readAt = :readAt "
          + "where n.userId = :userId and n.read = false")
  int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
