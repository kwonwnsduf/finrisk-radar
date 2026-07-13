package com.finrisk.radar.risk;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.financial.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.seed-reit-risk-scenarios.enabled", havingValue = "true")
public class ReitMetricScenarioSeedData implements ApplicationRunner {
  private final AssetRepository assets;
  private final ReitMetricRepository metrics;
  private final DebtMaturityRepository debts;
  private final CreditEventRepository events;

  public ReitMetricScenarioSeedData(
      AssetRepository assets,
      ReitMetricRepository metrics,
      DebtMaturityRepository debts,
      CreditEventRepository events) {
    this.assets = assets;
    this.metrics = metrics;
    this.debts = debts;
    this.events = events;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    Asset reit = assets.findFirstByTickerOrderByIdAsc("348950").orElse(null);
    if (reit == null || reit.getAssetType() != AssetType.REIT) return;
    LocalDate now = LocalDate.now();
    seedMetric(reit.getId(), now.minusMonths(12), false);
    seedMetric(reit.getId(), now.minusDays(1), true);

    for (int i = 3; i >= 0; i--) {
      LocalDate snapshot = now.minusMonths(i * 3L);
      LocalDate maturity = snapshot.plusMonths(6);
      DebtMaturity debt =
          DebtMaturity.createSnapshot(
              reit,
              maturity,
              new BigDecimal("180000000000"),
              DebtType.SHORT_TERM_BORROWING,
              new BigDecimal("8.20"),
              "KRW",
              true,
              snapshot,
              "REIT-SAMPLE-ROLLOVER-1");
      if (!debts.existsByAssetIdAndMaturityDateAndAmountAndDebtType(
          reit.getId(), maturity, debt.getAmount(), debt.getDebtType())) debts.save(debt);
    }
    seedEvent(reit.getId(), CreditEventType.REFINANCING_FAILURE, now.minusDays(30));
    seedEvent(reit.getId(), CreditEventType.RIGHTS_OFFERING_FAILURE, now.minusDays(25));
    seedEvent(reit.getId(), CreditEventType.BOND_ISSUANCE_FAILURE, now.minusDays(20));
    seedEvent(reit.getId(), CreditEventType.CREDIT_RATING_CCC_OR_BELOW, now.minusDays(10));
    seedEvent(reit.getId(), CreditEventType.REHABILITATION_FILED, now.minusDays(1));
  }

  private void seedMetric(Long assetId, LocalDate period, boolean stressed) {
    if (metrics.existsByAssetIdAndPeriod(assetId, period)) return;
    metrics.save(
        ReitMetric.create(
            assetId,
            period,
            bd(stressed ? "78" : "55"),
            bd("1000000000000"),
            bd(stressed ? "650000000000" : "1050000000000"),
            bd(stressed ? "720000000000" : "550000000000"),
            bd(stressed ? "45000000000" : "22000000000"),
            bd(stressed ? "38000000000" : "70000000000"),
            bd(stressed ? "28" : "8"),
            bd(stressed ? "135" : "82"),
            bd(stressed ? "8.2" : "4.1"),
            bd(stressed ? "65000000000" : "5000000000"),
            bd(stressed ? "80000000000" : "300000000000"),
            bd("65"),
            bd("75"),
            bd(stressed ? "85" : "20"),
            stressed,
            "SEED",
            "REIT-DAY10-SAMPLE-" + period,
            LocalDateTime.now()));
  }

  private void seedEvent(Long assetId, CreditEventType type, LocalDate date) {
    String key = "REIT-DAY10-" + type;
    if (events.existsByExternalEventKey(key)) return;
    events.save(
        CreditEvent.create(
            assetId, type, date, RiskSeverity.CRITICAL, "SEED", "REIT_SAMPLE", null,
            "Synthetic Day 10 REIT scenario", "REIT-DAY10", key));
  }

  private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
