package com.finrisk.radar.report.tool;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.financial.*;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.risk.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FinancialRiskTool {
  private final FinancialMetricService financials;
  private final DebtMaturityService debts;
  private final ReitMetricService reits;

  public FinancialRiskTool(
      FinancialMetricService financials, DebtMaturityService debts, ReitMetricService reits) {
    this.financials = financials;
    this.debts = debts;
    this.reits = reits;
  }

  public FinancialRiskData load(Asset asset) {
    var metrics = financials.getMetrics(asset.getId()).stream().limit(8).toList();
    LocalDate end = LocalDate.now().plusMonths(24);
    var maturities =
        debts.getDebtMaturities(asset.getId()).stream()
            .filter(
                x -> !x.maturityDate().isBefore(LocalDate.now()) && !x.maturityDate().isAfter(end))
            .limit(20)
            .toList();
    ReitMetric reit = null;
    ReitMetric previous = null;
    if (asset.getAssetType() == AssetType.REIT) {
      try {
        reit = reits.latestReitMetric(asset.getId(), LocalDate.now());
        previous = reits.previousReitMetric(asset.getId(), LocalDate.now()).orElse(null);
      } catch (BusinessException ignored) {
        /* represented as missing context */
      }
    }
    return new FinancialRiskData(metrics, maturities, reit, previous, LocalDate.now());
  }

  public record FinancialRiskData(
      List<FinancialMetricResponse> financialMetrics,
      List<DebtMaturityResponse> debtMaturities,
      ReitMetric latestReitMetric,
      ReitMetric previousReitMetric,
      LocalDate asOfDate) {}
}
