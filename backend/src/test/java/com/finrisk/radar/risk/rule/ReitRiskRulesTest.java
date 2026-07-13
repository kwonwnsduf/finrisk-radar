package com.finrisk.radar.risk.rule;

import static org.junit.jupiter.api.Assertions.*;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.financial.*;
import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.engine.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReitRiskRulesTest {
  private static final LocalDate AS_OF = LocalDate.of(2026, 7, 13);

  @Test
  void registersEveryReitRuleAsAnIndependentRule() {
    List<RiskRule> rules = rules(new ReitRiskRuleConfiguration());
    assertEquals(18, rules.size());
    assertEquals(18, rules.stream().map(RiskRule::supports).distinct().count());
    assertTrue(rules.stream().allMatch(rule -> rule.supportsAssetType(AssetType.REIT)));
    assertTrue(rules.stream().noneMatch(rule -> rule.supportsAssetType(AssetType.BOND_ISSUER)));
  }

  @Test
  void evaluatesStressedRelatedRulesWithoutSuppression() {
    Map<RiskRuleType, RiskRuleResult> results = new EnumMap<>(RiskRuleType.class);
    for (RiskRule rule : rules(new ReitRiskRuleConfiguration()))
      results.put(rule.supports(), rule.evaluate(stressedContext()));
    for (RiskRuleType type : List.of(
        RiskRuleType.LTV_SPIKE, RiskRuleType.ASSET_VALUE_DROP, RiskRuleType.VALUATION_GAP,
        RiskRuleType.COVENANT_HEADROOM_LOW, RiskRuleType.CASH_TRAP_COVENANT_BREACH,
        RiskRuleType.DEFAULT_COVENANT_BREACH, RiskRuleType.MATURITY_WALL,
        RiskRuleType.LIQUIDITY_COVERAGE_SHORTFALL, RiskRuleType.REFINANCING_FAILURE,
        RiskRuleType.REPEATED_SHORT_TERM_ROLLOVER))
      assertTrue(results.get(type).score() > 0, type.name());
  }

  @Test
  void registrySeparatesBondAndReitRules() {
    RiskRule bond = new RiskRule() {
      public int priority() { return 1; }
      public RiskRuleType supports() { return RiskRuleType.DEBT_RATIO; }
      public boolean required() { return true; }
      public RiskRuleResult evaluate(RiskEvaluationContext context) { return null; }
    };
    List<RiskRule> all = new ArrayList<>(rules(new ReitRiskRuleConfiguration()));
    all.add(bond);
    RiskRuleRegistry registry = new RiskRuleRegistry(all);
    assertEquals(18, registry.rulesFor(AssetType.REIT).size());
    assertEquals(List.of(bond), registry.rulesFor(AssetType.BOND_ISSUER));
  }

  private static List<RiskRule> rules(ReitRiskRuleConfiguration c) {
    return List.of(c.reitLtvSpikeRiskRule(), c.reitAssetValueDropRiskRule(),
        c.reitValuationGapRiskRule(), c.reitMaturityWallRiskRule(),
        c.reitRefinancingRateSpikeRiskRule(), c.reitRepeatedShortTermRolloverRiskRule(),
        c.reitRefinancingFailureRiskRule(), c.reitDividendStressRiskRule(),
        c.reitLiquidityCoverageShortfallRiskRule(), c.reitTrappedCashDependencyRiskRule(),
        c.reitFxHedgeBurdenRiskRule(), c.reitCovenantHeadroomLowRiskRule(),
        c.reitCashTrapCovenantBreachRiskRule(), c.reitDefaultCovenantBreachRiskRule(),
        c.reitRightsOfferingFailureRiskRule(), c.reitBondIssuanceFailureRiskRule(),
        c.reitCreditDowngradeRiskRule(), c.reitRehabilitationEventRiskRule());
  }

  private static RiskEvaluationContext stressedContext() {
    Asset asset = Asset.create("Generic Global REIT", "REIT1", "KOSPI", "REIT", "KR", "KRW", AssetType.REIT);
    ReitMetric previous = metric(AS_OF.minusYears(1), "55", "1050", "1000", "4.1", false);
    ReitMetric latest = metric(AS_OF.minusDays(1), "78", "1000", "650", "8.2", true);
    List<List<DebtMaturity>> snapshots = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      LocalDate snapshot = AS_OF.minusMonths(i * 3L);
      snapshots.add(List.of(DebtMaturity.createSnapshot(asset, AS_OF.plusMonths(5 + i), bd("400"),
          DebtType.SHORT_TERM_BORROWING, bd("8.2"), "KRW", true, snapshot, "ROLLOVER-1")));
    }
    List<CreditEvent> events = List.of(event(CreditEventType.REFINANCING_FAILURE, 20),
        event(CreditEventType.RIGHTS_OFFERING_FAILURE, 10),
        event(CreditEventType.BOND_ISSUANCE_FAILURE, 8),
        event(CreditEventType.CREDIT_RATING_CCC_OR_BELOW, 5),
        event(CreditEventType.REHABILITATION_FILED, 1));
    return new RiskEvaluationContext(asset, AS_OF, List.of(), snapshots.get(0), snapshots.get(1),
        snapshots, List.of(latest, previous), List.of(), events, List.of(), List.of());
  }

  private static ReitMetric metric(LocalDate period, String ltv, String book, String appraised,
      String rate, boolean trapped) {
    return ReitMetric.create(1L, period, bd(ltv), bd(book), bd(appraised), bd("720"), bd("45"),
        bd("38"), bd("28"), bd("135"), bd(rate), bd("65"), bd("80"), bd("65"), bd("75"),
        bd("85"), trapped, "TEST", "DOC", LocalDateTime.now());
  }

  private static CreditEvent event(CreditEventType type, int age) {
    return CreditEvent.create(1L, type, AS_OF.minusDays(age), RiskSeverity.CRITICAL, "TEST", "TEST",
        null, "test", type.name(), "TEST-" + type);
  }

  private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
