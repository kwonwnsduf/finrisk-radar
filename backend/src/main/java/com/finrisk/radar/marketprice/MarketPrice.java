package com.finrisk.radar.marketprice;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "market_prices", uniqueConstraints = @UniqueConstraint(
		name = "uk_market_prices_asset_date_source", columnNames = {"asset_id", "date", "source"}))
public class MarketPrice extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Column(nullable = false)
	private LocalDate date;

	@Column(nullable = false, precision = 20, scale = 6)
	private BigDecimal open;

	@Column(nullable = false, precision = 20, scale = 6)
	private BigDecimal high;

	@Column(nullable = false, precision = 20, scale = 6)
	private BigDecimal low;

	@Column(nullable = false, precision = 20, scale = 6)
	private BigDecimal close;

	@Column(nullable = false)
	private Long volume;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MarketPriceSource source;

	protected MarketPrice() {}

	public Long getId() { return id; }
	public Asset getAsset() { return asset; }
	public LocalDate getDate() { return date; }
	public BigDecimal getOpen() { return open; }
	public BigDecimal getHigh() { return high; }
	public BigDecimal getLow() { return low; }
	public BigDecimal getClose() { return close; }
	public Long getVolume() { return volume; }
	public MarketPriceSource getSource() { return source; }
}
