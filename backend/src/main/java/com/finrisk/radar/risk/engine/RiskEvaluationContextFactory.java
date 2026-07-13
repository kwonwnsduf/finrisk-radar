package com.finrisk.radar.risk.engine;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.financial.*;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.marketprice.*;
import com.finrisk.radar.risk.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RiskEvaluationContextFactory {
  private final AssetRepository assets;
  private final FinancialMetricRepository financials;
  private final DebtMaturityRepository debts;
  private final MarketPriceRepository prices;
  private final CreditEventRepository events;
  private final AssetRelationshipRepository relationships;
  private final ReitMetricRepository reitMetrics;

  public RiskEvaluationContextFactory(
      AssetRepository a,
      FinancialMetricRepository f,
      DebtMaturityRepository d,
      MarketPriceRepository p,
      CreditEventRepository e,
      AssetRelationshipRepository r,
      ReitMetricRepository rm) {
    assets = a;
    financials = f;
    debts = d;
    prices = p;
    events = e;
    relationships = r;
    reitMetrics = rm;
  }

  public RiskEvaluationContext create(Long assetId, LocalDate asOf) {
    Asset asset =
        assets
            .findById(assetId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
    List<FinancialMetric> fs = financials.findByAssetIdOrderByYearDescQuarterDesc(assetId);
    if (asset.getAssetType() == AssetType.BOND_ISSUER && fs.isEmpty())
      throw new BusinessException(ErrorCode.RISK_FINANCIAL_DATA_NOT_FOUND);
    List<ReitMetric> rms =
        asset.getAssetType() == AssetType.REIT
            ? reitMetrics.findByAssetIdAndPeriodLessThanEqualOrderByPeriodDesc(assetId, asOf)
            : List.of();
    if (asset.getAssetType() == AssetType.REIT && rms.isEmpty())
      throw new BusinessException(ErrorCode.RISK_REIT_METRIC_NOT_FOUND);
    List<DebtMaturity> all = debts.findByAssetIdOrderByMaturityDateAsc(assetId);
    List<LocalDate> snapshots =
        all.stream()
            .map(DebtMaturity::getSnapshotDate)
            .filter(Objects::nonNull)
            .filter(d -> !d.isAfter(asOf))
            .distinct()
            .sorted(Comparator.reverseOrder())
            .toList();
    List<DebtMaturity> ds =
        snapshots.isEmpty()
            ? all
            : all.stream().filter(d -> snapshots.get(0).equals(d.getSnapshotDate())).toList();
    List<DebtMaturity> previous =
        snapshots.size() < 2
            ? List.of()
            : all.stream().filter(d -> snapshots.get(1).equals(d.getSnapshotDate())).toList();
    List<List<DebtMaturity>> debtSnapshots =
        snapshots.stream()
            .limit(4)
            .map(snapshot -> all.stream().filter(d -> snapshot.equals(d.getSnapshotDate())).toList())
            .toList();
    List<MarketPrice> ps =
        asset.getMarketPriceAssetId() == null
            ? List.of()
            : prices.findAllByAsset_IdAndDateLessThanEqualOrderByDateAsc(
                asset.getMarketPriceAssetId(), asOf);
    if (ps.size() > 141) ps = ps.subList(ps.size() - 141, ps.size());
    List<AssetRelationship> rs =
        relationships.findByFromAssetId(assetId).stream()
            .filter(
                r ->
                    !r.getEffectiveFrom().isAfter(asOf)
                        && (r.getEffectiveTo() == null || !r.getEffectiveTo().isBefore(asOf)))
            .toList();
    List<CreditEvent> related =
        rs.stream()
            .flatMap(
                r ->
                    events
                        .findByAssetIdAndEventDateLessThanEqualOrderByEventDateDesc(
                            r.getToAssetId(), asOf)
                        .stream())
            .toList();
    return new RiskEvaluationContext(
        asset,
        asOf,
        List.copyOf(fs),
        List.copyOf(ds),
        List.copyOf(previous),
        List.copyOf(debtSnapshots),
        List.copyOf(rms),
        List.copyOf(ps),
        events.findByAssetIdAndEventDateLessThanEqualOrderByEventDateDesc(assetId, asOf),
        List.copyOf(related),
        List.copyOf(rs));
  }
}
