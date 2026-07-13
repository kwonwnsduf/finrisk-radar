package com.finrisk.radar.financial;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebtMaturityRepository extends JpaRepository<DebtMaturity, Long> {
  List<DebtMaturity> findByAssetIdOrderByMaturityDateAsc(Long assetId);

  List<DebtMaturity> findByAssetIdAndSnapshotDateOrderByMaturityDateAsc(
      Long assetId, LocalDate snapshotDate);

  boolean existsByAssetIdAndMaturityDateAndAmountAndDebtType(
      Long assetId, LocalDate maturityDate, BigDecimal amount, DebtType debtType);
}
