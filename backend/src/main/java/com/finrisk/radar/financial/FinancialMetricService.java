package com.finrisk.radar.financial;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialMetricService {
  private final FinancialMetricRepository repository;
  private final AssetRepository assets;

  public FinancialMetricService(FinancialMetricRepository repository, AssetRepository assets) {
    this.repository = repository;
    this.assets = assets;
  }

  @Transactional
  public FinancialMetric upsert(
      Asset asset, Integer year, Integer quarter, FinancialMetricValues values) {
    FinancialMetric metric =
        repository
            .findByAssetIdAndYearAndQuarter(asset.getId(), year, quarter)
            .orElseGet(() -> FinancialMetric.create(asset, year, quarter, values));
    metric.apply(values);
    return repository.save(metric);
  }

  @Transactional
  public FinancialMetric upsertCollected(
      Asset asset, Integer year, Integer quarter, FinancialMetricValues values) {
    FinancialMetric metric = upsert(asset, year, quarter, values);
    metric.collected(
        periodEndDate(year, quarter), DartReportCode.fromQuarter(quarter), LocalDateTime.now());
    return repository.save(metric);
  }

  private LocalDate periodEndDate(Integer year, Integer quarter) {
    return switch (quarter) {
      case 1 -> LocalDate.of(year, 3, 31);
      case 2 -> LocalDate.of(year, 6, 30);
      case 3 -> LocalDate.of(year, 9, 30);
      case 4 -> LocalDate.of(year, 12, 31);
      default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
    };
  }

  @Transactional(readOnly = true)
  public List<FinancialMetricResponse> getMetrics(Long assetId) {
    if (!assets.existsById(assetId)) throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
    return repository.findByAssetIdOrderByYearDescQuarterDesc(assetId).stream()
        .map(FinancialMetricResponse::from)
        .toList();
  }
}
