package com.finrisk.radar.risk;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReitMetricRepository extends JpaRepository<ReitMetric, Long> {
  List<ReitMetric> findByAssetIdOrderByPeriodDesc(Long assetId);
  List<ReitMetric> findByAssetIdAndPeriodLessThanEqualOrderByPeriodDesc(
      Long assetId, LocalDate period);
  Optional<ReitMetric> findFirstByAssetIdAndPeriodLessThanEqualOrderByPeriodDesc(
      Long assetId, LocalDate period);
  boolean existsByAssetIdAndPeriod(Long assetId, LocalDate period);
}
