package com.finrisk.radar.risk;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskSignalRepository extends JpaRepository<RiskSignal, Long> {
  List<RiskSignal> findByRiskScoreIdOrderByScoreDescIdAsc(Long riskScoreId);
}
