package com.finrisk.radar.document;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "asset_aliases")
public class AssetAlias extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(nullable = false)
  private String alias;

  @Column(name = "normalized_alias", nullable = false)
  private String normalizedAlias;

  @Enumerated(EnumType.STRING)
  @Column(name = "alias_type", nullable = false)
  private AssetAliasType aliasType;

  protected AssetAlias() {}

  public static AssetAlias create(
      Long asset, String alias, String normalized, AssetAliasType type) {
    AssetAlias a = new AssetAlias();
    a.assetId = asset;
    a.alias = alias;
    a.normalizedAlias = normalized;
    a.aliasType = type;
    return a;
  }

  public Long getAssetId() {
    return assetId;
  }

  public String getAlias() {
    return alias;
  }

  public String getNormalizedAlias() {
    return normalizedAlias;
  }
}
