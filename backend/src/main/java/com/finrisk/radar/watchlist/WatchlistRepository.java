package com.finrisk.radar.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {
	long countByUser_Id(Long userId);
	boolean existsByUser_IdAndAsset_Id(Long userId, Long assetId);
	List<WatchlistItem> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
	Optional<WatchlistItem> findByIdAndUser_Id(Long id, Long userId);
}
