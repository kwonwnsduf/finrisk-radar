package com.finrisk.radar.risk;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.global.error.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReitMetricService {
  private final AssetRepository assets;
  private final ReitMetricRepository metrics;

  public ReitMetricService(AssetRepository assets, ReitMetricRepository metrics) {
    this.assets = assets;
    this.metrics = metrics;
  }

  @Transactional
  public ReitMetric save(ReitMetric metric) {
    Asset asset = assets.findById(metric.getAssetId())
        .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
    if (asset.getAssetType() != AssetType.REIT)
      throw new BusinessException(ErrorCode.RISK_ASSET_NOT_SUPPORTED);
    if (metrics.existsByAssetIdAndPeriod(metric.getAssetId(), metric.getPeriod()))
      throw new IllegalArgumentException("A REIT metric already exists for the period");
    return metrics.save(metric);
  }

  @Transactional(readOnly = true)
  public ReitMetric latestReitMetric(Long assetId, LocalDate asOf) {
    return metrics.findFirstByAssetIdAndPeriodLessThanEqualOrderByPeriodDesc(assetId, asOf)
        .orElseThrow(() -> new BusinessException(ErrorCode.RISK_REIT_METRIC_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public Optional<ReitMetric> previousReitMetric(Long assetId, LocalDate asOf) {
    List<ReitMetric> values =
        metrics.findByAssetIdAndPeriodLessThanEqualOrderByPeriodDesc(assetId, asOf);
    return values.size() > 1 ? Optional.of(values.get(1)) : Optional.empty();
  }
}
