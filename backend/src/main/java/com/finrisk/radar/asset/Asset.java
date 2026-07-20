package com.finrisk.radar.asset;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;

@Entity
@Table(
    name = "assets",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_assets_ticker_market",
            columnNames = {"ticker", "market"}))
public class Asset extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, length = 100)
  private String ticker;

  @Column(length = 50)
  private String market;

  @Column(length = 100)
  private String sector;

  @Column(length = 10)
  private String country;

  @Column(length = 10)
  private String currency;

  @Column(name = "market_price_asset_id")
  private Long marketPriceAssetId;

  @Column(name = "dart_corp_code", length = 20)
  private String dartCorpCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "asset_type", nullable = false, length = 30)
  private AssetType assetType;

  protected Asset() {}

  private Asset(
      String name,
      String ticker,
      String market,
      String sector,
      String country,
      String currency,
      AssetType assetType) {
    apply(name, ticker, market, sector, country, currency, assetType);
  }

  public static Asset create(
      String name,
      String ticker,
      String market,
      String sector,
      String country,
      String currency,
      AssetType assetType) {
    return new Asset(name, ticker, market, sector, country, currency, assetType);
  }

  public void canonicalize(
      String name,
      String market,
      String sector,
      String country,
      String currency,
      AssetType assetType) {
    apply(name, ticker, market, sector, country, currency, assetType);
  }

  public void assignDartCorpCode(String dartCorpCode) {
    this.dartCorpCode = trimToNull(dartCorpCode);
  }

  private void apply(
      String name,
      String ticker,
      String market,
      String sector,
      String country,
      String currency,
      AssetType assetType) {
    this.name = name.trim();
    this.ticker = normalizeCode(ticker);
    this.market = normalizeNullableCode(market);
    this.sector = trimToNull(sector);
    this.country = normalizeNullableCode(country);
    this.currency = normalizeNullableCode(currency);
    this.assetType = assetType;
  }

  private static String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeNullableCode(String value) {
    String trimmed = trimToNull(value);
    return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getTicker() {
    return ticker;
  }

  public String getMarket() {
    return market;
  }

  public String getSector() {
    return sector;
  }

  public String getCountry() {
    return country;
  }

  public String getCurrency() {
    return currency;
  }

  public AssetType getAssetType() {
    return assetType;
  }

  public Long getMarketPriceAssetId() {
    return marketPriceAssetId;
  }

  public String getDartCorpCode() {
    return dartCorpCode;
  }
}
