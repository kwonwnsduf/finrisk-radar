package com.finrisk.radar.risk;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "asset_relationships")
public class AssetRelationship extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "from_asset_id", nullable = false)
  private Long fromAssetId;

  @Column(name = "to_asset_id", nullable = false)
  private Long toAssetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "relationship_type", nullable = false)
  private AssetRelationshipType relationshipType;

  @Column(name = "exposure_amount")
  private BigDecimal exposureAmount;

  private String currency;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  private String description;

  protected AssetRelationship() {}

  public static AssetRelationship create(
      Long from,
      Long to,
      AssetRelationshipType type,
      BigDecimal exposure,
      String currency,
      LocalDate fromDate,
      LocalDate toDate,
      String description) {
    AssetRelationship r = new AssetRelationship();
    r.fromAssetId = from;
    r.toAssetId = to;
    r.relationshipType = type;
    r.exposureAmount = exposure;
    r.currency = currency;
    r.effectiveFrom = fromDate;
    r.effectiveTo = toDate;
    r.description = description;
    return r;
  }

  public Long getId() {
    return id;
  }

  public Long getFromAssetId() {
    return fromAssetId;
  }

  public Long getToAssetId() {
    return toAssetId;
  }

  public AssetRelationshipType getRelationshipType() {
    return relationshipType;
  }

  public BigDecimal getExposureAmount() {
    return exposureAmount;
  }

  public String getCurrency() {
    return currency;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
  }

  public String getDescription() {
    return description;
  }
}
