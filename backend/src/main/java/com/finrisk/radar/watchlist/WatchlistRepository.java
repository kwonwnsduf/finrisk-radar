package com.finrisk.radar.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {
	long countByUserId(Long userId);
	boolean existsByUserIdAndAssetCode(Long userId, String assetCode);
	List<WatchlistItem> findAllByUserIdOrderByCreatedAtDesc(Long userId);
	Optional<WatchlistItem> findByIdAndUserId(Long id, Long userId);
}
