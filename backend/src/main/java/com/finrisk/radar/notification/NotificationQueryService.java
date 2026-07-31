package com.finrisk.radar.notification;

import static com.finrisk.radar.notification.NotificationModels.*;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationQueryService {
  private final NotificationRepository notifications;

  public NotificationQueryService(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  @Transactional(readOnly = true)
  public PageResponse list(
      Long userId, Boolean read, NotificationType type, int page, int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(0, page),
            Math.min(Math.max(1, size), 50),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    Specification<Notification> specification =
        (root, query, builder) -> {
          var predicates = new ArrayList<Predicate>();
          predicates.add(builder.equal(root.get("userId"), userId));
          if (read != null) predicates.add(builder.equal(root.get("read"), read));
          if (type != null) predicates.add(builder.equal(root.get("type"), type));
          return builder.and(predicates.toArray(Predicate[]::new));
        };
    return PageResponse.from(notifications.findAll(specification, pageable));
  }

  @Transactional(readOnly = true)
  public UnreadCount unreadCount(Long userId) {
    return new UnreadCount(notifications.countByUserIdAndReadFalse(userId));
  }
}
