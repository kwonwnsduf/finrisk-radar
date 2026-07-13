package com.finrisk.radar.risk;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {
  Optional<RiskScore> findByJobId(UUID jobId);

  Optional<RiskScore> findFirstByAssetIdOrderByCalculatedAtDescIdDesc(Long assetId);

  List<RiskScore> findByAssetIdOrderByCalculatedAtDescIdDesc(Long assetId);
}
