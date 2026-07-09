package com.finrisk.radar.financial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancialMetricRepository extends JpaRepository<FinancialMetric, Long> {
	Optional<FinancialMetric> findByAssetIdAndYearAndQuarter(Long assetId, Integer year, Integer quarter);
	List<FinancialMetric> findByAssetIdOrderByYearDescQuarterDesc(Long assetId);
}
