package com.finrisk.radar.risk;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditEventRepository extends JpaRepository<CreditEvent, Long> {
  boolean existsByExternalEventKey(String key);

  List<CreditEvent> findByAssetIdAndEventDateLessThanEqualOrderByEventDateDesc(
      Long assetId, LocalDate date);

  List<CreditEvent> findByAssetIdOrderByEventDateDesc(Long assetId);
}
