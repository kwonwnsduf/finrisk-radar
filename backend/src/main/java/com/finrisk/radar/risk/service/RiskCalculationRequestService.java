package com.finrisk.radar.risk.service;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.event.*;
import com.finrisk.radar.risk.kafka.*;
import com.finrisk.radar.user.UserRepository;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class RiskCalculationRequestService {
  public static final String VERSION = "corporate-risk-v1";
  private final UserRepository users;
  private final AssetRepository assets;
  private final RiskCalculationJobService jobs;
  private final RiskEventPublisher publisher;

  public RiskCalculationRequestService(
      UserRepository u, AssetRepository a, RiskCalculationJobService j, RiskEventPublisher p) {
    users = u;
    assets = a;
    jobs = j;
    publisher = p;
  }

  public RiskCalculationJob request(Long userId, Long assetId) {
    if (!users.existsById(userId)) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    Asset asset =
        assets
            .findById(assetId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
    if (asset.getAssetType() != AssetType.BOND_ISSUER)
      throw new BusinessException(ErrorCode.RISK_ASSET_NOT_SUPPORTED);
    RiskCalculationJob job;
    try {
      job = jobs.create(userId, assetId, VERSION);
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.RISK_CALCULATION_ALREADY_RUNNING);
    }
    try {
      publisher.publishRequested(
          new RiskScoreRequestedEvent(job.getJobId(), assetId, userId, Instant.now()));
    } catch (RiskEventPublishException e) {
      jobs.fail(
          job.getJobId(),
          ErrorCode.RISK_KAFKA_PUBLISH_FAILED.getCode(),
          ErrorCode.RISK_KAFKA_PUBLISH_FAILED.getMessage());
      throw new BusinessException(ErrorCode.RISK_KAFKA_PUBLISH_FAILED);
    }
    return job;
  }
}
