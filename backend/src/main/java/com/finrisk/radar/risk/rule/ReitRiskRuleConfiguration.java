package com.finrisk.radar.risk.rule;

import com.finrisk.radar.asset.AssetType;
import com.finrisk.radar.financial.DebtMaturity;
import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.engine.*;
import java.math.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.annotation.*;

@Configuration
public class ReitRiskRuleConfiguration {
  @Bean RiskRule reitLtvSpikeRiskRule() { return new ReitLtvSpikeRiskRule(); }
  @Bean RiskRule reitAssetValueDropRiskRule() { return new ReitAssetValueDropRiskRule(); }
  @Bean RiskRule reitValuationGapRiskRule() { return new ReitValuationGapRiskRule(); }
  @Bean RiskRule reitMaturityWallRiskRule() { return new ReitMaturityWallRiskRule(); }
  @Bean RiskRule reitRefinancingRateSpikeRiskRule() { return new ReitRefinancingRateSpikeRiskRule(); }
  @Bean RiskRule reitRepeatedShortTermRolloverRiskRule() { return new ReitRepeatedShortTermRolloverRiskRule(); }
  @Bean RiskRule reitRefinancingFailureRiskRule() { return new ReitRefinancingFailureRiskRule(); }
  @Bean RiskRule reitDividendStressRiskRule() { return new ReitDividendStressRiskRule(); }
  @Bean RiskRule reitLiquidityCoverageShortfallRiskRule() { return new ReitLiquidityCoverageShortfallRiskRule(); }
  @Bean RiskRule reitTrappedCashDependencyRiskRule() { return new ReitTrappedCashDependencyRiskRule(); }
  @Bean RiskRule reitFxHedgeBurdenRiskRule() { return new ReitFxHedgeBurdenRiskRule(); }
  @Bean RiskRule reitCovenantHeadroomLowRiskRule() { return new ReitCovenantHeadroomLowRiskRule(); }
  @Bean RiskRule reitCashTrapCovenantBreachRiskRule() { return new ReitCashTrapCovenantBreachRiskRule(); }
  @Bean RiskRule reitDefaultCovenantBreachRiskRule() { return new ReitDefaultCovenantBreachRiskRule(); }
  @Bean RiskRule reitRightsOfferingFailureRiskRule() { return new ReitRightsOfferingFailureRiskRule(); }
  @Bean RiskRule reitBondIssuanceFailureRiskRule() { return new ReitBondIssuanceFailureRiskRule(); }
  @Bean RiskRule reitCreditDowngradeRiskRule() { return new ReitCreditDowngradeRiskRule(); }
  @Bean RiskRule reitRehabilitationEventRiskRule() { return new ReitRehabilitationEventRiskRule(); }
  @Bean RiskRule reitDocumentCreditEventRiskRule() { return new ReitDocumentCreditEventRiskRule(); }
}

abstract class ReitRiskRule implements RiskRule {
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private final int priority;
  private final RiskRuleType type;
  private final RiskCategory category;
  private final int maxScore;
  private final boolean required;

  ReitRiskRule(int priority, RiskRuleType type, RiskCategory category, int maxScore, boolean required) {
    this.priority = priority;
    this.type = type;
    this.category = category;
    this.maxScore = maxScore;
    this.required = required;
  }

  public final int priority() { return priority; }
  public final RiskRuleType supports() { return type; }
  public final boolean required() { return required; }
  public final boolean supportsAssetType(AssetType assetType) { return assetType == AssetType.REIT; }
  final RiskRuleResult na(String message) { return RiskRuleResult.unavailable(type, category, maxScore, message); }
  final RiskRuleResult ok(int score, String message, Map<String, Object> evidence, Long sourceId) {
    return RiskRuleResult.calculated(type, category, score, maxScore, type.name(), message, evidence, sourceId);
  }
  final ReitMetric latest(RiskEvaluationContext context) { return context.latestReitMetric(); }
  final ReitMetric previous(RiskEvaluationContext context) { return context.previousReitMetric(); }
  static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
    return numerator.multiply(HUNDRED).divide(denominator, 6, RoundingMode.HALF_UP);
  }
  static Map<String, Object> evidence(Object... values) {
    Map<String, Object> evidence = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i += 2)
      if (values[i + 1] != null) evidence.put((String) values[i], values[i + 1]);
    return evidence;
  }
  static BigDecimal sixMonthMaturity(RiskEvaluationContext context) {
    return context.debts().stream()
        .filter(d -> !d.getMaturityDate().isBefore(context.asOfDate()))
        .filter(d -> !d.getMaturityDate().isAfter(context.asOfDate().plusMonths(6)))
        .map(DebtMaturity::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
  final Optional<CreditEvent> latestEvent(RiskEvaluationContext context, CreditEventType... types) {
    Set<CreditEventType> accepted = Set.of(types);
    return context.creditEvents().stream().filter(e -> accepted.contains(e.getEventType())).findFirst();
  }
}

final class ReitLtvSpikeRiskRule extends ReitRiskRule {
  ReitLtvSpikeRiskRule() { super(10100, RiskRuleType.LTV_SPIKE, RiskCategory.FINANCIAL, 5, true); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    ReitMetric n = latest(c), p = previous(c);
    if (p == null || n.getLtv() == null || p.getLtv() == null) return na("Current and previous LTV are required");
    BigDecimal change = n.getLtv().subtract(p.getLtv());
    int score = change.compareTo(new BigDecimal("10")) >= 0 ? 5 : change.compareTo(new BigDecimal("5")) >= 0 ? 3 : change.compareTo(new BigDecimal("3")) >= 0 ? 1 : 0;
    return ok(score, "REIT LTV change evaluated", evidence("currentLtv", n.getLtv(), "previousLtv", p.getLtv(), "changePercentagePoints", change, "threshold", "3/5/10pp"), n.getId());
  }
}

final class ReitAssetValueDropRiskRule extends ReitRiskRule {
  ReitAssetValueDropRiskRule() { super(10200, RiskRuleType.ASSET_VALUE_DROP, RiskCategory.MARKET, 6, true); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    ReitMetric n = latest(c), p = previous(c);
    if (p == null) return na("A previous REIT metric is required");
    BigDecimal current = n.getAppraisedAssetValue(), prior = p.getAppraisedAssetValue();
    String basis = "APPRAISED";
    if (current == null || prior == null) { current = n.getBookAssetValue(); prior = p.getBookAssetValue(); basis = "BOOK"; }
    if (current == null || prior == null || prior.signum() <= 0) return na("Comparable asset values are required");
    BigDecimal drop = percent(prior.subtract(current), prior);
    int score = drop.compareTo(new BigDecimal("30")) >= 0 ? 6 : drop.compareTo(new BigDecimal("20")) >= 0 ? 4 : drop.compareTo(new BigDecimal("10")) >= 0 ? 2 : 0;
    return ok(score, "REIT asset value change evaluated", evidence("basis", basis, "currentValue", current, "previousValue", prior, "dropPercent", drop, "threshold", "10/20/30%"), n.getId());
  }
}

final class ReitValuationGapRiskRule extends ReitRiskRule {
  ReitValuationGapRiskRule() { super(10300, RiskRuleType.VALUATION_GAP, RiskCategory.MARKET, 4, true); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    ReitMetric m = latest(c);
    if (m.getBookAssetValue() == null || m.getAppraisedAssetValue() == null || m.getBookAssetValue().signum() <= 0) return na("Book and appraised values are required");
    BigDecimal gap = percent(m.getBookAssetValue().subtract(m.getAppraisedAssetValue()), m.getBookAssetValue());
    int score = gap.compareTo(new BigDecimal("30")) >= 0 ? 4 : gap.compareTo(new BigDecimal("20")) >= 0 ? 2 : gap.compareTo(new BigDecimal("10")) >= 0 ? 1 : 0;
    return ok(score, "REIT valuation gap evaluated", evidence("bookAssetValue", m.getBookAssetValue(), "appraisedAssetValue", m.getAppraisedAssetValue(), "gapPercent", gap, "threshold", "10/20/30%"), m.getId());
  }
}

final class ReitMaturityWallRiskRule extends ReitRiskRule {
  ReitMaturityWallRiskRule() { super(10400, RiskRuleType.MATURITY_WALL, RiskCategory.LIQUIDITY_MATURITY, 6, true); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    ReitMetric m = latest(c);
    if (m.getTotalBorrowings() == null || m.getTotalBorrowings().signum() <= 0 || c.debts().isEmpty()) return na("Borrowings and debt maturities are required");
    BigDecimal due = sixMonthMaturity(c), ratio = percent(due, m.getTotalBorrowings());
    int score = ratio.compareTo(new BigDecimal("50")) >= 0 ? 6 : ratio.compareTo(new BigDecimal("30")) >= 0 ? 4 : ratio.compareTo(new BigDecimal("20")) >= 0 ? 2 : 0;
    return ok(score, "Six-month maturity concentration evaluated", evidence("maturityWithinSixMonths", due, "totalBorrowings", m.getTotalBorrowings(), "concentrationPercent", ratio, "threshold", "20/30/50%"), m.getId());
  }
}

final class ReitRefinancingRateSpikeRiskRule extends ReitRiskRule {
  ReitRefinancingRateSpikeRiskRule() { super(10500, RiskRuleType.REFINANCING_RATE_SPIKE, RiskCategory.FINANCIAL, 5, true); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    ReitMetric n = latest(c), p = previous(c);
    if (p == null || n.getRefinancingRate() == null || p.getRefinancingRate() == null) return na("Current and previous refinancing rates are required");
    BigDecimal basisPoints = n.getRefinancingRate().subtract(p.getRefinancingRate()).multiply(new BigDecimal("100"));
    int score = basisPoints.compareTo(new BigDecimal("300")) >= 0 ? 5 : basisPoints.compareTo(new BigDecimal("200")) >= 0 ? 3 : basisPoints.compareTo(new BigDecimal("100")) >= 0 ? 2 : 0;
    return ok(score, "REIT refinancing rate change evaluated", evidence("currentRate", n.getRefinancingRate(), "previousRate", p.getRefinancingRate(), "changeBasisPoints", basisPoints, "threshold", "100/200/300bp"), n.getId());
  }
}

final class ReitRepeatedShortTermRolloverRiskRule extends ReitRiskRule {
  ReitRepeatedShortTermRolloverRiskRule() { super(10600, RiskRuleType.REPEATED_SHORT_TERM_ROLLOVER, RiskCategory.LIQUIDITY_MATURITY, 4, false); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    if (c.debtSnapshots().size() < 2) return na("At least two debt snapshots are required");
    Map<String, Integer> occurrences = new HashMap<>();
    for (List<DebtMaturity> snapshot : c.debtSnapshots())
      for (DebtMaturity debt : snapshot)
        if (debt.getExternalDebtKey() != null && (debt.isShortTerm() || debt.getMaturityDate().isBefore(c.asOfDate().plusYears(1))))
          occurrences.merge(debt.getExternalDebtKey(), 1, Integer::sum);
    int repeats = occurrences.values().stream().mapToInt(v -> Math.max(0, v - 1)).max().orElse(0);
    if (occurrences.isEmpty()) return na("Stable external debt keys are required for rollover detection");
    int score = repeats >= 3 ? 4 : repeats == 2 ? 2 : repeats == 1 ? 1 : 0;
    return ok(score, "Repeated short-term rollover evaluated", evidence("snapshotCount", c.debtSnapshots().size(), "maximumRepeatCount", repeats, "threshold", "1/2/3 repeats"), null);
  }
}

final class ReitRefinancingFailureRiskRule extends ReitRiskRule {
  ReitRefinancingFailureRiskRule() { super(10700, RiskRuleType.REFINANCING_FAILURE, RiskCategory.LIQUIDITY_MATURITY, 6, false); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    Optional<CreditEvent> event = latestEvent(c, CreditEventType.REFINANCING_FAILURE);
    if (event.isEmpty()) return ok(0, "No refinancing failure registered", evidence("registered", false), null);
    long age = ChronoUnit.DAYS.between(event.get().getEventDate(), c.asOfDate());
    int score = age <= 90 ? 6 : age <= 180 ? 4 : age <= 365 ? 2 : 0;
    return ok(score, "REIT refinancing failure evaluated", evidence("eventDate", event.get().getEventDate(), "ageDays", age, "threshold", "90/180/365 days"), event.get().getId());
  }
}

final class ReitDividendStressRiskRule extends ReitRiskRule {
  ReitDividendStressRiskRule() { super(10800, RiskRuleType.DIVIDEND_STRESS, RiskCategory.FINANCIAL, 6, true); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    ReitMetric m = latest(c);
    if (m.getDividendPayoutRatio() == null) return na("Dividend payout ratio is required");
    int score = m.getDividendPayoutRatio().compareTo(new BigDecimal("120")) >= 0 ? 6 : m.getDividendPayoutRatio().compareTo(new BigDecimal("100")) >= 0 ? 4 : m.getDividendPayoutRatio().compareTo(new BigDecimal("90")) >= 0 ? 2 : 0;
    if (m.getRentalIncome() != null && m.getInterestExpense() != null && m.getRentalIncome().compareTo(m.getInterestExpense()) <= 0) score = Math.max(score, 4);
    return ok(score, "REIT dividend sustainability evaluated", evidence("dividendPayoutRatio", m.getDividendPayoutRatio(), "rentalIncome", m.getRentalIncome(), "interestExpense", m.getInterestExpense(), "vacancyRate", m.getVacancyRate(), "threshold", "90/100/120%"), m.getId());
  }
}

final class ReitLiquidityCoverageShortfallRiskRule extends ReitRiskRule {
  ReitLiquidityCoverageShortfallRiskRule() { super(10900, RiskRuleType.LIQUIDITY_COVERAGE_SHORTFALL, RiskCategory.LIQUIDITY_MATURITY, 8, true); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    ReitMetric m = latest(c); BigDecimal due = sixMonthMaturity(c);
    if (m.getAvailableLiquidity() == null || c.debts().isEmpty()) return na("Liquidity and debt maturities are required");
    if (due.signum() == 0) return ok(0, "No debt matures within six months", evidence("maturityWithinSixMonths", due), m.getId());
    BigDecimal ratio = m.getAvailableLiquidity().divide(due, 6, RoundingMode.HALF_UP);
    int score = ratio.compareTo(new BigDecimal("0.4")) < 0 ? 8 : ratio.compareTo(new BigDecimal("0.7")) < 0 ? 5 : ratio.compareTo(BigDecimal.ONE) < 0 ? 3 : 0;
    return ok(score, "REIT six-month liquidity coverage evaluated", evidence("availableLiquidity", m.getAvailableLiquidity(), "maturityWithinSixMonths", due, "coverageRatio", ratio, "threshold", "1.0/0.7/0.4"), m.getId());
  }
}

final class ReitTrappedCashDependencyRiskRule extends ReitRiskRule {
  ReitTrappedCashDependencyRiskRule() { super(11000, RiskRuleType.TRAPPED_CASH_DEPENDENCY, RiskCategory.GROUP_CONTAGION, 5, true); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    ReitMetric m = latest(c);
    if (m.getForeignCashDependencyRatio() == null) return na("Foreign cash dependency ratio is required");
    int score = m.getForeignCashDependencyRatio().compareTo(new BigDecimal("70")) >= 0 ? 5 : m.getForeignCashDependencyRatio().compareTo(new BigDecimal("50")) >= 0 ? 3 : m.getForeignCashDependencyRatio().compareTo(new BigDecimal("30")) >= 0 ? 1 : 0;
    if (m.isCashTrapFlag() && m.getForeignCashDependencyRatio().compareTo(new BigDecimal("30")) >= 0) score = 5;
    return ok(score, "Foreign trapped-cash dependency evaluated", evidence("foreignCashDependencyRatio", m.getForeignCashDependencyRatio(), "cashTrapFlag", m.isCashTrapFlag(), "threshold", "30/50/70%"), m.getId());
  }
}

final class ReitFxHedgeBurdenRiskRule extends ReitRiskRule {
  ReitFxHedgeBurdenRiskRule() { super(11100, RiskRuleType.FX_HEDGE_BURDEN, RiskCategory.FINANCIAL, 5, true); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    ReitMetric m = latest(c);
    if (m.getFxHedgeSettlement() == null || m.getAvailableLiquidity() == null || m.getAvailableLiquidity().signum() <= 0) return na("FX hedge settlement and positive liquidity are required");
    BigDecimal ratio = percent(m.getFxHedgeSettlement(), m.getAvailableLiquidity());
    int score = ratio.compareTo(new BigDecimal("50")) >= 0 ? 5 : ratio.compareTo(new BigDecimal("25")) >= 0 ? 3 : ratio.compareTo(new BigDecimal("10")) >= 0 ? 1 : 0;
    return ok(score, "FX hedge settlement burden evaluated", evidence("fxHedgeSettlement", m.getFxHedgeSettlement(), "availableLiquidity", m.getAvailableLiquidity(), "burdenPercent", ratio, "threshold", "10/25/50%"), m.getId());
  }
}

final class ReitCovenantHeadroomLowRiskRule extends ReitRiskRule {
  ReitCovenantHeadroomLowRiskRule() { super(11200, RiskRuleType.COVENANT_HEADROOM_LOW, RiskCategory.FINANCIAL, 4, true); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    ReitMetric m = latest(c);
    if (m.getLtv() == null || m.getCashTrapThreshold() == null) return na("LTV and cash-trap threshold are required");
    BigDecimal headroom = m.getCashTrapThreshold().subtract(m.getLtv());
    int score = headroom.compareTo(new BigDecimal("2")) < 0 ? 4 : headroom.compareTo(new BigDecimal("5")) < 0 ? 2 : headroom.compareTo(new BigDecimal("10")) < 0 ? 1 : 0;
    return ok(score, "Cash-trap covenant headroom evaluated", evidence("ltv", m.getLtv(), "cashTrapThreshold", m.getCashTrapThreshold(), "headroomPercentagePoints", headroom, "threshold", "10/5/2pp"), m.getId());
  }
}

final class ReitCashTrapCovenantBreachRiskRule extends ReitRiskRule {
  ReitCashTrapCovenantBreachRiskRule() { super(11300, RiskRuleType.CASH_TRAP_COVENANT_BREACH, RiskCategory.LIQUIDITY_MATURITY, 7, true); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    ReitMetric m = latest(c);
    if (m.getLtv() == null || m.getCashTrapThreshold() == null) return na("LTV and cash-trap threshold are required");
    boolean breached = m.isCashTrapFlag() || m.getLtv().compareTo(m.getCashTrapThreshold()) >= 0;
    return ok(breached ? 7 : 0, "Cash-trap covenant evaluated", evidence("ltv", m.getLtv(), "cashTrapThreshold", m.getCashTrapThreshold(), "cashTrapFlag", m.isCashTrapFlag(), "breached", breached), m.getId());
  }
}

final class ReitDefaultCovenantBreachRiskRule extends ReitRiskRule {
  ReitDefaultCovenantBreachRiskRule() { super(11400, RiskRuleType.DEFAULT_COVENANT_BREACH, RiskCategory.LIQUIDITY_MATURITY, 10, true); }
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    ReitMetric m = latest(c);
    if (m.getLtv() == null || m.getDefaultLtvThreshold() == null) return na("LTV and default threshold are required");
    boolean breached = m.getLtv().compareTo(m.getDefaultLtvThreshold()) >= 0;
    return ok(breached ? 10 : 0, "Default LTV covenant evaluated", evidence("ltv", m.getLtv(), "defaultLtvThreshold", m.getDefaultLtvThreshold(), "breached", breached), m.getId());
  }
}

abstract class ReitEventRiskRule extends ReitRiskRule {
  private final Set<CreditEventType> eventTypes;
  ReitEventRiskRule(int priority, RiskRuleType type, int max, CreditEventType... eventTypes) {
    super(priority, type, RiskCategory.CREDIT_EVENT, max, false); this.eventTypes = Set.of(eventTypes);
  }
  int score(CreditEvent event) { return maxForEvent(); }
  abstract int maxForEvent();
  public RiskRuleResult evaluate(RiskEvaluationContext c) {
    Optional<CreditEvent> event = c.creditEvents().stream().filter(e -> eventTypes.contains(e.getEventType())).findFirst();
    if (event.isEmpty()) return ok(0, "No matching REIT credit event registered", evidence("registered", false), null);
    int score = score(event.get());
    return ok(score, "REIT credit event detected", evidence("eventType", event.get().getEventType(), "eventDate", event.get().getEventDate(), "severity", event.get().getSeverity()), event.get().getId());
  }
}

final class ReitRightsOfferingFailureRiskRule extends ReitEventRiskRule {
  ReitRightsOfferingFailureRiskRule() { super(11500, RiskRuleType.RIGHTS_OFFERING_FAILURE, 4, CreditEventType.RIGHTS_OFFERING_FAILURE); }
  int maxForEvent() { return 4; }
}
final class ReitBondIssuanceFailureRiskRule extends ReitEventRiskRule {
  ReitBondIssuanceFailureRiskRule() { super(11600, RiskRuleType.BOND_ISSUANCE_FAILURE, 5, CreditEventType.BOND_ISSUANCE_FAILURE); }
  int maxForEvent() { return 5; }
}
final class ReitCreditDowngradeRiskRule extends ReitEventRiskRule {
  ReitCreditDowngradeRiskRule() { super(11700, RiskRuleType.CREDIT_DOWNGRADE, 5, CreditEventType.CREDIT_RATING_DOWNGRADE, CreditEventType.SPECULATIVE_GRADE_ENTRY, CreditEventType.CREDIT_RATING_CCC_OR_BELOW); }
  int maxForEvent() { return 5; }
  int score(CreditEvent event) {
    return switch (event.getEventType()) {
      case CREDIT_RATING_CCC_OR_BELOW -> 5;
      case SPECULATIVE_GRADE_ENTRY -> 3;
      default -> 2;
    };
  }
}
final class ReitRehabilitationEventRiskRule extends ReitEventRiskRule {
  ReitRehabilitationEventRiskRule() { super(11800, RiskRuleType.REHABILITATION_EVENT, 11, CreditEventType.REHABILITATION_FILED); }
  int maxForEvent() { return 11; }
}

final class ReitDocumentCreditEventRiskRule extends ReitEventRiskRule {
  ReitDocumentCreditEventRiskRule() { super(11900, RiskRuleType.DOCUMENT_CREDIT_EVENT, 8,
      CreditEventType.LIQUIDITY_CRISIS, CreditEventType.FX_HEDGE_STRESS,
      CreditEventType.CASH_TRAP_TRIGGERED, CreditEventType.DIVIDEND_REDUCTION,
      CreditEventType.LTV_COVENANT_BREACH); }
  int maxForEvent() { return 8; }
  int score(CreditEvent event) {
    return switch (event.getEventType()) {
      case CASH_TRAP_TRIGGERED, LTV_COVENANT_BREACH, LIQUIDITY_CRISIS -> 8;
      case FX_HEDGE_STRESS -> 5;
      case DIVIDEND_REDUCTION -> 3;
      default -> 0;
    };
  }
}
