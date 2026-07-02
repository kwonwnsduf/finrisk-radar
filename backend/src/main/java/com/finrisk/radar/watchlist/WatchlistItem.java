package com.finrisk.radar.watchlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist_items")
public class WatchlistItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	// Temporary Day3 identifier. A future migration can add asset_id without changing this entity's identity.
	@Column(name = "asset_code", nullable = false, length = 100)
	private String assetCode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected WatchlistItem() {}

	private WatchlistItem(Long userId, String assetCode) {
		this.userId = userId;
		this.assetCode = assetCode;
	}

	public static WatchlistItem create(Long userId, String assetCode) {
		return new WatchlistItem(userId, assetCode);
	}

	@PrePersist
	void onCreate() { createdAt = LocalDateTime.now(); }

	public Long getId() { return id; }
	public Long getUserId() { return userId; }
	public String getAssetCode() { return assetCode; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
