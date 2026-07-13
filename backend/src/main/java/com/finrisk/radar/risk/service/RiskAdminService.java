package com.finrisk.radar.risk.service;

import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.api.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskAdminService {
  private final AssetRepository assets;
  private final CreditEventRepository events;
  private final AssetRelationshipRepository relationships;

  public RiskAdminService(
      AssetRepository a, CreditEventRepository e, AssetRelationshipRepository r) {
    assets = a;
    events = e;
    relationships = r;
  }

  @Transactional
  public CreditEvent createEvent(Long assetId, CreditEventCreateRequest r) {
    if (!assets.existsById(assetId)) throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
    if (events.existsByExternalEventKey(r.externalEventKey()))
      throw new BusinessException(ErrorCode.RISK_CREDIT_EVENT_DUPLICATED);
    return events.save(
        CreditEvent.create(
            assetId,
            r.eventType(),
            r.eventDate(),
            r.severity(),
            r.sourceType(),
            r.sourceName(),
            r.sourceDocumentId(),
            r.description(),
            r.incidentKey(),
            r.externalEventKey()));
  }

  @Transactional(readOnly = true)
  public List<CreditEvent> events(Long assetId) {
    if (!assets.existsById(assetId)) throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
    return events.findByAssetIdOrderByEventDateDesc(assetId);
  }

  @Transactional
  public AssetRelationship relationship(AssetRelationshipCreateRequest r) {
    if (r.fromAssetId().equals(r.toAssetId())
        || !assets.existsById(r.fromAssetId())
        || !assets.existsById(r.toAssetId())
        || (r.effectiveTo() != null && r.effectiveTo().isBefore(r.effectiveFrom())))
      throw new BusinessException(ErrorCode.RISK_RELATIONSHIP_INVALID);
    return relationships.save(
        AssetRelationship.create(
            r.fromAssetId(),
            r.toAssetId(),
            r.relationshipType(),
            r.exposureAmount(),
            r.currency(),
            r.effectiveFrom(),
            r.effectiveTo(),
            r.description()));
  }
}
