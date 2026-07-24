package com.finrisk.radar.backtest;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestJobRepository extends JpaRepository<BacktestJob, UUID> {
  Page<BacktestJob> findByRequestedByUserIdAndStatusOrderByCreatedAtDesc(
      Long userId, BacktestStatus status, Pageable pageable);
}
