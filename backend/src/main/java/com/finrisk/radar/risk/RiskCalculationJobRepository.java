package com.finrisk.radar.risk;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RiskCalculationJobRepository extends JpaRepository<RiskCalculationJob, UUID> {
  boolean existsByAssetIdAndStatusIn(Long assetId, Collection<RiskCalculationStatus> statuses);

  @Modifying
  @Query(
      value =
          "UPDATE risk_calculation_jobs SET status='RUNNING', started_at=:started,"
              + " updated_at=:started WHERE job_id=:id AND status='REQUESTED'",
      nativeQuery = true)
  int markRunning(@Param("id") UUID id, @Param("started") LocalDateTime started);
}
