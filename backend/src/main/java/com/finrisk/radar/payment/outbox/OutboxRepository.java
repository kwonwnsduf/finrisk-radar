package com.finrisk.radar.payment.outbox;

import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
  List<OutboxEvent> findByStatusOrderByOccurredAtAsc(OutboxStatus status, Pageable pageable);

  boolean existsByEventKey(String key);
}
