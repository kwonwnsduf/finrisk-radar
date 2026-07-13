package com.finrisk.radar.risk;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.financial.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.seed-risk-scenarios.enabled", havingValue = "true")
public class RiskScenarioSeedData implements ApplicationRunner {

  private final AssetRepository assets;
  private final FinancialMetricService metrics;
  private final DebtMaturityRepository debts;
  private final CreditEventRepository events;
  private final AssetRelationshipRepository relationships;

  public RiskScenarioSeedData(
      AssetRepository a,
      FinancialMetricService m,
      DebtMaturityRepository d,
      CreditEventRepository e,
      AssetRelationshipRepository r) {
    this.assets = a;
    this.metrics = m;
    this.debts = d;
    this.events = e;
    this.relationships = r;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    // 1. 대상 자산(JTBC) 검증
    Asset jtbc = assets.findFirstByTickerOrderByIdAsc("JTBC").orElse(null);
    if (jtbc == null) {
      return;
    }

    // 2. 2026년 1~4분기 가상 재무 지표(Financial Metrics) 등록
    // 영업이익 손실, 높은 부채 등 위험 시나리오용 수치 세팅
    FinancialMetricValues dummyMetrics =
        new FinancialMetricValues(
            new BigDecimal("200000000000"), // 매출액
            new BigDecimal("-10000000000"), // 영업이익 (operationIncome - 손실)
            new BigDecimal("-15000000000"), // 당기순이익 (netIncome - 손실)
            new BigDecimal("600000000000"), // 총부채
            new BigDecimal("100000000000"), // 자기자본
            new BigDecimal("10000000000"), // 이자비용 (interestExpense)
            new BigDecimal("-20000000000"), // 영업현금흐름 (operationCashFlow - 음수)
            new BigDecimal("16000000000"), // 현금성자산
            new BigDecimal("600"), // 부채비율 (debtRatio - 600%)
            new BigDecimal("-0.625") // 이자보상배율 (interestCoverageRatio - 음수)
            );

    for (int q = 1; q <= 4; q++) {
      metrics.upsert(jtbc, 2026, q, dummyMetrics);
    }

    // 3. 만기 임박 단기 채무 데이터 생성 및 중복 체크 후 저장
    LocalDate now = LocalDate.now();

    List<DebtMaturity> sampleDebts =
        List.of(
            DebtMaturity.create(
                jtbc,
                now.plusDays(45),
                new BigDecimal("150000000000"),
                DebtType.ABSTB,
                new BigDecimal("7.5"),
                "KRW",
                true),
            DebtMaturity.create(
                jtbc,
                now.plusDays(120),
                new BigDecimal("100000000000"),
                DebtType.CP,
                new BigDecimal("7.0"),
                "KRW",
                true));

    sampleDebts.forEach(
        d -> {
          boolean isExist =
              debts.existsByAssetIdAndMaturityDateAndAmountAndDebtType(
                  jtbc.getId(), d.getMaturityDate(), d.getAmount(), d.getDebtType());
          if (!isExist) {
            debts.save(d);
          }
        });

    // 4. 악재(Credit Event) 시나리오 순차적 추가 (차환실패 -> 연체 -> 회생신청)
    seedEvent(
        jtbc.getId(),
        CreditEventType.REFINANCING_FAILURE,
        "JTBC-SAMPLE-REFINANCING",
        now.minusDays(30));
    seedEvent(
        jtbc.getId(), CreditEventType.PAYMENT_DEFAULT, "JTBC-SAMPLE-DEFAULT", now.minusDays(10));
    seedEvent(
        jtbc.getId(),
        CreditEventType.REHABILITATION_FILED,
        "JTBC-SAMPLE-REHABILITATION",
        now.minusDays(1));
  }

  private void seedEvent(Long asset, CreditEventType type, String key, LocalDate date) {
    if (!events.existsByExternalEventKey(key)) {
      events.save(
          CreditEvent.create(
              asset,
              type,
              date,
              RiskSeverity.CRITICAL,
              "SEED",
              "JTBC_SAMPLE",
              null,
              "Synthetic Day 9 scenario",
              "JTBC-SAMPLE",
              key));
    }
  }
}
