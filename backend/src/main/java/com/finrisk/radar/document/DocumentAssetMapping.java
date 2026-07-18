package com.finrisk.radar.document;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "document_asset_mappings")
public class DocumentAssetMapping extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "match_method", nullable = false)
  private AssetMatchMethod matchMethod;

  @Column(name = "matched_value", nullable = false)
  private String matchedValue;

  @Column(nullable = false)
  private BigDecimal confidence;

  @Column(name = "is_primary", nullable = false)
  private boolean primary;

  protected DocumentAssetMapping() {}

  public static DocumentAssetMapping create(
      Long doc,
      Long asset,
      AssetMatchMethod method,
      String value,
      BigDecimal confidence,
      boolean primary) {
    DocumentAssetMapping m = new DocumentAssetMapping();
    m.documentId = doc;
    m.assetId = asset;
    m.matchMethod = method;
    m.matchedValue = value;
    m.confidence = confidence;
    m.primary = primary;
    return m;
  }

  public Long getId() {
    return id;
  }

  public Long getDocumentId() {
    return documentId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public BigDecimal getConfidence() {
    return confidence;
  }

  public AssetMatchMethod getMatchMethod() {
    return matchMethod;
  }
}
