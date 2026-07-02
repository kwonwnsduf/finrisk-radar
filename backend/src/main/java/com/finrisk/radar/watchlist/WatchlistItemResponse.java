package com.finrisk.radar.watchlist;

import java.time.LocalDateTime;

public record WatchlistItemResponse(Long id, String assetCode, LocalDateTime createdAt) {
	public static WatchlistItemResponse from(WatchlistItem item) {
		return new WatchlistItemResponse(item.getId(), item.getAssetCode(), item.getCreatedAt());
	}
}
