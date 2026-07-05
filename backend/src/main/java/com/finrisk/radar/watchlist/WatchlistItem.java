package com.finrisk.radar.watchlist;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist_items")
public class WatchlistItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected WatchlistItem() {}

	private WatchlistItem(User user, Asset asset) {
		this.user = user;
		this.asset = asset;
	}

	public static WatchlistItem create(User user, Asset asset) {
		return new WatchlistItem(user, asset);
	}

	@PrePersist
	void onCreate() { createdAt = LocalDateTime.now(); }

	public Long getId() { return id; }
	public User getUser() { return user; }
	public Asset getAsset() { return asset; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
