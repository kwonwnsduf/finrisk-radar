package com.finrisk.radar.financial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DebtMaturityRepository extends JpaRepository<DebtMaturity, Long> {
	List<DebtMaturity> findByAssetIdOrderByMaturityDateAsc(Long assetId);
	boolean existsByAssetIdAndMaturityDateAndAmountAndDebtType(
			Long assetId, LocalDate maturityDate, BigDecimal amount, DebtType debtType);
}
