package com.finrisk.radar.collector.log;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
import java.util.UUID;

public interface CollectionLogRepository extends JpaRepository<CollectionLog, UUID> {
  long countByStatusAndCompletedAtAfter(CollectionStatus status, LocalDateTime after);
  Page<CollectionLog> findByStatusOrderByCompletedAtDesc(CollectionStatus status, Pageable pageable);
}
