package com.finrisk.radar.backtest;

import java.util.UUID;
import java.util.Collection;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestJobRepository extends JpaRepository<BacktestJob, UUID> {
  Page<BacktestJob> findByRequestedByUserIdAndStatusOrderByCreatedAtDesc(
      Long userId, BacktestStatus status, Pageable pageable);

  long countByStatusIn(Collection<BacktestStatus> statuses);

  long countByStatusAndCompletedAtAfter(BacktestStatus status, java.time.LocalDateTime after);

  Page<BacktestJob> findByStatusOrderByCompletedAtDesc(
      BacktestStatus status, Pageable pageable);
}
