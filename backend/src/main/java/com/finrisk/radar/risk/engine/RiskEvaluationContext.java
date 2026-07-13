package com.finrisk.radar.risk.engine;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.financial.*;
import com.finrisk.radar.marketprice.MarketPrice;
import com.finrisk.radar.risk.*;
import java.time.LocalDate;
import java.util.List;

public record RiskEvaluationContext(
    Asset asset,
    LocalDate asOfDate,
    List<FinancialMetric> financials,
    List<DebtMaturity> debts,
    List<DebtMaturity> previousDebts,
    List<List<DebtMaturity>> debtSnapshots,
    List<ReitMetric> reitMetrics,
    List<MarketPrice> prices,
    List<CreditEvent> creditEvents,
    List<CreditEvent> relatedCreditEvents,
    List<AssetRelationship> relationships) {
  public FinancialMetric latest() {
    return financials.isEmpty() ? null : financials.get(0);
  }

  public ReitMetric latestReitMetric() {
    return reitMetrics.isEmpty() ? null : reitMetrics.get(0);
  }

  public ReitMetric previousReitMetric() {
    return reitMetrics.size() < 2 ? null : reitMetrics.get(1);
  }
}
