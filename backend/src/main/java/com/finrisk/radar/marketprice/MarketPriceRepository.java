package com.finrisk.radar.marketprice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {
	List<MarketPrice> findAllByAsset_IdAndDateBetweenOrderByDateAsc(
			Long assetId, LocalDate startDate, LocalDate endDate);

	List<MarketPrice> findAllByAsset_IdAndDateGreaterThanEqualOrderByDateAsc(Long assetId, LocalDate startDate);

	List<MarketPrice> findAllByAsset_IdAndDateLessThanEqualOrderByDateAsc(Long assetId, LocalDate endDate);

	List<MarketPrice> findAllByAsset_IdOrderByDateAsc(Long assetId);
}
