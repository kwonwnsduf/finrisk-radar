package com.finrisk.radar.risk.rule;

import com.finrisk.radar.financial.*;
import com.finrisk.radar.marketprice.MarketPrice;
import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.engine.*;
import java.math.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.annotation.*;

@Configuration
public class StandardRiskRuleConfiguration {
  private static final RiskCategory FIN = RiskCategory.FINANCIAL,
      LIQ = RiskCategory.LIQUIDITY_MATURITY,
      MARKET = RiskCategory.MARKET;

  @Bean
  RiskRule debtRatioRiskRule() {
    return rule(
        100,
        RiskRuleType.DEBT_RATIO,
        true,
        c -> {
          FinancialMetric m = c.latest();
          if (m.getTotalDebt() == null || m.getTotalEquity() == null)
            return na(RiskRuleType.DEBT_RATIO, FIN, 5, "Debt or equity is unavailable");
          BigDecimal ratio =
              m.getTotalEquity().signum() <= 0
                  ? new BigDecimal("9999")
                  : m.getTotalDebt()
                      .multiply(new BigDecimal("100"))
                      .divide(m.getTotalEquity(), 6, RoundingMode.HALF_UP);
          int s =
              ratio.compareTo(new BigDecimal("500")) >= 0
                  ? 5
                  : ratio.compareTo(new BigDecimal("300")) >= 0
                      ? 4
                      : ratio.compareTo(new BigDecimal("200")) >= 0
                          ? 2
                          : ratio.compareTo(new BigDecimal("100")) >= 0 ? 1 : 0;
          return ok(
              RiskRuleType.DEBT_RATIO,
              FIN,
              s,
              5,
              m.getTotalEquity().signum() <= 0 ? "CAPITAL_IMPAIRMENT" : "HIGH_DEBT_RATIO",
              "Debt ratio evaluated",
              Map.of("currentValue", ratio, "threshold", "100/200/300/500"),
              m.getId());
        });
  }

  @Bean
  RiskRule interestCoverageRiskRule() {
    return rule(
        200,
        RiskRuleType.INTEREST_COVERAGE,
        true,
        c -> {
          FinancialMetric m = c.latest();
          if (m.getOperatingIncome() == null || m.getInterestExpense() == null)
            return na(
                RiskRuleType.INTEREST_COVERAGE,
                FIN,
                7,
                "Operating income or interest expense is unavailable");
          if (m.getOperatingIncome().signum() < 0)
            return ok(
                RiskRuleType.INTEREST_COVERAGE,
                FIN,
                7,
                7,
                "OPERATING_LOSS",
                "Operating loss detected",
                Map.of("operatingIncome", m.getOperatingIncome()),
                m.getId());
          if (m.getInterestExpense().signum() == 0)
            return ok(
                RiskRuleType.INTEREST_COVERAGE,
                FIN,
                0,
                7,
                "INTEREST_COVERAGE_LOW",
                "No interest expense",
                Map.of("interestExpense", 0),
                m.getId());
          BigDecimal r =
              m.getOperatingIncome().divide(m.getInterestExpense(), 6, RoundingMode.HALF_UP);
          int s =
              r.compareTo(BigDecimal.ONE) < 0
                  ? 6
                  : r.compareTo(new BigDecimal("1.5")) < 0
                      ? 4
                      : r.compareTo(new BigDecimal("2")) < 0
                          ? 2
                          : r.compareTo(new BigDecimal("3")) < 0 ? 1 : 0;
          return ok(
              RiskRuleType.INTEREST_COVERAGE,
              FIN,
              s,
              7,
              "INTEREST_COVERAGE_LOW",
              "Interest coverage evaluated",
              Map.of("currentValue", r),
              m.getId());
        });
  }

  @Bean
  RiskRule operatingCashFlowRiskRule() {
    return rule(
        300,
        RiskRuleType.OPERATING_CASH_FLOW,
        true,
        c -> {
          List<FinancialMetric> ms = c.financials().stream().limit(4).toList();
          if (ms.isEmpty() || ms.get(0).getOperatingCashFlow() == null)
            return na(
                RiskRuleType.OPERATING_CASH_FLOW, FIN, 7, "Operating cash flow is unavailable");
          long negatives =
              ms.stream()
                  .filter(
                      m ->
                          m.getOperatingCashFlow() != null && m.getOperatingCashFlow().signum() < 0)
                  .count();
          int consecutive = 0;
          for (FinancialMetric m : ms) {
            if (m.getOperatingCashFlow() != null && m.getOperatingCashFlow().signum() < 0)
              consecutive++;
            else break;
          }
          int s =
              consecutive >= 4
                  ? 7
                  : negatives >= 3 ? 6 : consecutive >= 2 ? 4 : consecutive >= 1 ? 2 : 0;
          return ok(
              RiskRuleType.OPERATING_CASH_FLOW,
              FIN,
              s,
              7,
              s >= 4 ? "PERSISTENT_CASH_FLOW_DEFICIT" : "NEGATIVE_OPERATING_CASH_FLOW",
              "Operating cash flow trend evaluated",
              Map.of("negativePeriods", negatives, "consecutiveNegativePeriods", consecutive),
              ms.get(0).getId());
        });
  }

  @Bean
  RiskRule netLossRiskRule() {
    return rule(
        400,
        RiskRuleType.NET_LOSS,
        true,
        c -> {
          List<FinancialMetric> annual =
              c.financials().stream().filter(m -> m.getQuarter() == 4).limit(3).toList();
          FinancialMetric latest = c.latest();
          if (latest.getTotalEquity() != null && latest.getTotalEquity().signum() <= 0)
            return ok(
                RiskRuleType.NET_LOSS,
                FIN,
                6,
                6,
                "CAPITAL_IMPAIRMENT",
                "Capital impairment detected",
                Map.of("totalEquity", latest.getTotalEquity()),
                latest.getId());
          if (annual.isEmpty() || annual.get(0).getNetIncome() == null)
            return na(RiskRuleType.NET_LOSS, FIN, 6, "Annual net income is unavailable");
          int consecutive = 0;
          for (FinancialMetric m : annual) {
            if (m.getNetIncome() != null && m.getNetIncome().signum() < 0) consecutive++;
            else break;
          }
          int s = consecutive >= 3 ? 5 : consecutive >= 2 ? 4 : consecutive == 1 ? 2 : 0;
          return ok(
              RiskRuleType.NET_LOSS,
              FIN,
              s,
              6,
              s >= 2 ? "PERSISTENT_NET_LOSS" : "NET_LOSS",
              "Annual net income evaluated",
              Map.of("consecutiveLossYears", consecutive),
              annual.get(0).getId());
        });
  }

  @Bean
  RiskRule maturityConcentrationRiskRule() {
    return rule(
        1000,
        RiskRuleType.MATURITY_CONCENTRATION,
        true,
        c -> {
          List<DebtMaturity> ds = future(c);
          if (ds.isEmpty())
            return na(
                RiskRuleType.MATURITY_CONCENTRATION, LIQ, 10, "Debt maturity data is unavailable");
          BigDecimal total = sum(ds),
              six = sum(ds.stream().filter(d -> days(c, d) <= 180).toList());
          if (total.signum() == 0)
            return ok(
                RiskRuleType.MATURITY_CONCENTRATION,
                LIQ,
                0,
                10,
                "MATURITY_WALL",
                "No outstanding maturity",
                Map.of("totalDebt", 0),
                null);
          BigDecimal pct =
              six.multiply(new BigDecimal("100")).divide(total, 6, RoundingMode.HALF_UP);
          int s =
              pct.compareTo(new BigDecimal("50")) >= 0
                  ? 10
                  : pct.compareTo(new BigDecimal("40")) >= 0
                      ? 8
                      : pct.compareTo(new BigDecimal("30")) >= 0
                          ? 6
                          : pct.compareTo(new BigDecimal("20")) >= 0
                              ? 4
                              : pct.compareTo(new BigDecimal("10")) >= 0 ? 2 : 0;
          return ok(
              RiskRuleType.MATURITY_CONCENTRATION,
              LIQ,
              s,
              10,
              "MATURITY_WALL",
              "Six-month maturity concentration evaluated",
              Map.of("maturityWithin180Days", six, "totalMaturity", total, "percentage", pct),
              null);
        });
  }

  @Bean
  RiskRule shortTermDebtDependencyRiskRule() {
    return rule(
        1100,
        RiskRuleType.SHORT_TERM_DEBT_DEPENDENCY,
        true,
        c -> {
          List<DebtMaturity> ds = future(c);
          if (ds.isEmpty())
            return na(
                RiskRuleType.SHORT_TERM_DEBT_DEPENDENCY,
                LIQ,
                8,
                "Debt maturity data is unavailable");
          BigDecimal total = sum(ds),
              shortDebt = sum(ds.stream().filter(d -> shortTerm(c, d)).toList());
          if (total.signum() == 0)
            return ok(
                RiskRuleType.SHORT_TERM_DEBT_DEPENDENCY,
                LIQ,
                0,
                8,
                "SHORT_TERM_DEBT_DEPENDENCY",
                "No financial debt",
                Map.of("totalDebt", 0),
                null);
          BigDecimal pct =
              shortDebt.multiply(new BigDecimal("100")).divide(total, 6, RoundingMode.HALF_UP);
          int s =
              pct.compareTo(new BigDecimal("80")) >= 0
                  ? 8
                  : pct.compareTo(new BigDecimal("60")) >= 0
                      ? 6
                      : pct.compareTo(new BigDecimal("40")) >= 0
                          ? 4
                          : pct.compareTo(new BigDecimal("20")) >= 0 ? 2 : 0;
          return ok(
              RiskRuleType.SHORT_TERM_DEBT_DEPENDENCY,
              LIQ,
              s,
              8,
              "SHORT_TERM_DEBT_DEPENDENCY",
              "Short-term debt dependency evaluated",
              Map.of("shortTermDebt", shortDebt, "percentage", pct),
              null);
        });
  }

  @Bean
  RiskRule cashMaturityCoverageRiskRule() {
    return rule(1200, RiskRuleType.CASH_MATURITY_COVERAGE, true, c -> coverage(c, false));
  }

  @Bean
  RiskRule liquidityCoverageRiskRule() {
    return rule(1300, RiskRuleType.LIQUIDITY_COVERAGE, true, c -> coverage(c, true));
  }

  @Bean
  RiskRule maturityTrendRiskRule() {
    return rule(
        1400,
        RiskRuleType.MATURITY_TREND,
        false,
        c -> {
          if (c.previousDebts().isEmpty())
            return na(
                RiskRuleType.MATURITY_TREND, LIQ, 4, "A previous debt snapshot is unavailable");
          BigDecimal current = sum(future(c).stream().filter(d -> days(c, d) <= 180).toList());
          BigDecimal previous =
              sum(
                  c.previousDebts().stream()
                      .filter(
                          d ->
                              !d.getMaturityDate().isBefore(c.asOfDate())
                                  && ChronoUnit.DAYS.between(c.asOfDate(), d.getMaturityDate())
                                      <= 180)
                      .toList());
          if (previous.signum() == 0)
            return na(RiskRuleType.MATURITY_TREND, LIQ, 4, "Previous six-month maturity is zero");
          BigDecimal increase =
              current
                  .subtract(previous)
                  .multiply(new BigDecimal("100"))
                  .divide(previous, 6, RoundingMode.HALF_UP);
          int s =
              increase.compareTo(new BigDecimal("60")) >= 0
                  ? 4
                  : increase.compareTo(new BigDecimal("30")) >= 0 ? 2 : 0;
          return ok(
              RiskRuleType.MATURITY_TREND,
              LIQ,
              s,
              4,
              "MATURITY_CONCENTRATION_INCREASE",
              "Maturity trend evaluated",
              Map.of("current", current, "previous", previous, "increasePercent", increase),
              null);
        });
  }

  @Bean
  RiskRule priceDropRiskRule() {
    return rule(
        2000,
        RiskRuleType.PRICE_DROP,
        false,
        c -> {
          List<MarketPrice> ps = c.prices();
          if (ps.size() < 21)
            return na(RiskRuleType.PRICE_DROP, MARKET, 4, "At least 21 prices are required");
          BigDecimal first = ps.get(ps.size() - 21).getClose(),
              last = ps.get(ps.size() - 1).getClose();
          if (first.signum() == 0)
            return na(RiskRuleType.PRICE_DROP, MARKET, 4, "Baseline price is zero");
          BigDecimal ret =
              last.subtract(first)
                  .multiply(new BigDecimal("100"))
                  .divide(first, 6, RoundingMode.HALF_UP);
          int s =
              ret.compareTo(new BigDecimal("-30")) <= 0
                  ? 4
                  : ret.compareTo(new BigDecimal("-20")) <= 0
                      ? 2
                      : ret.compareTo(new BigDecimal("-10")) <= 0 ? 1 : 0;
          return ok(
              RiskRuleType.PRICE_DROP,
              MARKET,
              s,
              4,
              "PRICE_CRASH",
              "Twenty-day return evaluated",
              Map.of("returnPercent", ret),
              ps.get(ps.size() - 1).getId());
        });
  }

  @Bean
  RiskRule volatilitySpikeRiskRule() {
    return rule(
        2100,
        RiskRuleType.VOLATILITY_SPIKE,
        false,
        c ->
            na(
                RiskRuleType.VOLATILITY_SPIKE,
                MARKET,
                3,
                "Volatility comparison requires a complete 140-day sample"));
  }

  @Bean
  RiskRule volumeAnomalyRiskRule() {
    return rule(
        2200,
        RiskRuleType.VOLUME_ANOMALY,
        false,
        c -> {
          List<MarketPrice> ps = c.prices();
          if (ps.size() < 80)
            return na(RiskRuleType.VOLUME_ANOMALY, MARKET, 3, "At least 80 prices are required");
          double
              recent =
                  ps.subList(ps.size() - 20, ps.size()).stream()
                      .mapToLong(MarketPrice::getVolume)
                      .average()
                      .orElse(0),
              previous =
                  ps.subList(0, ps.size() - 20).stream()
                      .mapToLong(MarketPrice::getVolume)
                      .average()
                      .orElse(0);
          if (previous == 0)
            return na(RiskRuleType.VOLUME_ANOMALY, MARKET, 3, "Previous volume is zero");
          double ratio = recent / previous;
          int s = ratio >= 5 ? 3 : ratio >= 3 ? 2 : ratio >= 2 ? 1 : 0;
          return ok(
              RiskRuleType.VOLUME_ANOMALY,
              MARKET,
              s,
              3,
              "ABNORMAL_VOLUME",
              "Volume ratio evaluated",
              Map.of("volumeRatio", ratio),
              null);
        });
  }

  @Bean
  RiskRule creditEventRiskRule() {
    return rule(
        3000,
        RiskRuleType.CREDIT_EVENT,
        true,
        c -> {
          if (c.creditEvents().isEmpty())
            return ok(
                RiskRuleType.CREDIT_EVENT,
                RiskCategory.CREDIT_EVENT,
                0,
                25,
                "NO_CREDIT_EVENT",
                "No registered credit event",
                Map.of("registeredEventCount", 0),
                null);
          CreditEvent e =
              c.creditEvents().stream()
                  .max(Comparator.comparingInt(x -> eventScore(x.getEventType())))
                  .orElseThrow();
          int score = eventScore(e.getEventType());
          return ok(
              RiskRuleType.CREDIT_EVENT,
              RiskCategory.CREDIT_EVENT,
              score,
              25,
              e.getEventType().name(),
              "Credit event detected",
              Map.of("eventType", e.getEventType(), "eventDate", e.getEventDate()),
              e.getId());
        });
  }

  @Bean
  RiskRule groupContagionRiskRule() {
    return rule(
        4000,
        RiskRuleType.GROUP_CONTAGION,
        false,
        c -> {
          if (c.relationships().isEmpty())
            return na(
                RiskRuleType.GROUP_CONTAGION,
                RiskCategory.GROUP_CONTAGION,
                5,
                "Relationship data is unavailable");
          if (c.relatedCreditEvents().isEmpty())
            return ok(
                RiskRuleType.GROUP_CONTAGION,
                RiskCategory.GROUP_CONTAGION,
                0,
                5,
                "GROUP_CONTAGION_RISK",
                "No related-asset credit event",
                Map.of("relationshipCount", c.relationships().size()),
                null);
          Set<CreditEventType> types =
              c.relatedCreditEvents().stream()
                  .map(CreditEvent::getEventType)
                  .collect(java.util.stream.Collectors.toSet());
          int score =
              c.relationships().stream()
                  .mapToInt(
                      r ->
                          r.getRelationshipType() == AssetRelationshipType.CROSS_DEFAULT
                                  && types.stream()
                                      .anyMatch(StandardRiskRuleConfiguration::isDefaultEvent)
                              ? 5
                              : r.getRelationshipType() == AssetRelationshipType.CREDIT_SUPPORT
                                      && types.contains(CreditEventType.REHABILITATION_FILED)
                                  ? 4
                                  : r.getRelationshipType()
                                              == AssetRelationshipType.PAYMENT_GUARANTEE
                                          && types.contains(CreditEventType.PAYMENT_DEFAULT)
                                      ? 3
                                      : 0)
                  .max()
                  .orElse(0);
          return ok(
              RiskRuleType.GROUP_CONTAGION,
              RiskCategory.GROUP_CONTAGION,
              score,
              5,
              "GROUP_CONTAGION_RISK",
              "Group relationship and related credit event evaluated",
              Map.of(
                  "relationshipCount",
                  c.relationships().size(),
                  "relatedEventCount",
                  c.relatedCreditEvents().size()),
              null);
        });
  }

  private RiskRuleResult coverage(RiskEvaluationContext c, boolean ocf) {
    RiskRuleType type = ocf ? RiskRuleType.LIQUIDITY_COVERAGE : RiskRuleType.CASH_MATURITY_COVERAGE;
    int max = ocf ? 4 : 9;
    FinancialMetric m = c.latest();
    if (m.getCash() == null) return na(type, LIQ, max, "Cash is unavailable");
    long horizon = ocf ? 365 : 180;
    BigDecimal due = sum(future(c).stream().filter(d -> days(c, d) <= horizon).toList());
    if (due.signum() == 0)
      return ok(
          type,
          LIQ,
          0,
          max,
          "LOW_LIQUIDITY_COVERAGE",
          "No debt matures in the horizon",
          Map.of("maturityDebt", 0),
          null);
    BigDecimal numerator = m.getCash();
    if (ocf && m.getOperatingCashFlow() != null && m.getOperatingCashFlow().signum() > 0)
      numerator = numerator.add(m.getOperatingCashFlow());
    BigDecimal ratio = numerator.divide(due, 6, RoundingMode.HALF_UP);
    int s =
        ocf
            ? (ratio.compareTo(new BigDecimal(".2")) < 0
                ? 4
                : ratio.compareTo(new BigDecimal(".4")) < 0
                    ? 3
                    : ratio.compareTo(new BigDecimal(".7")) < 0
                        ? 2
                        : ratio.compareTo(BigDecimal.ONE) < 0 ? 1 : 0)
            : (ratio.compareTo(new BigDecimal(".2")) < 0
                ? 9
                : ratio.compareTo(new BigDecimal(".4")) < 0
                    ? 7
                    : ratio.compareTo(new BigDecimal(".7")) < 0
                        ? 5
                        : ratio.compareTo(BigDecimal.ONE) < 0
                            ? 3
                            : ratio.compareTo(new BigDecimal("1.5")) < 0 ? 1 : 0);
    return ok(
        type,
        LIQ,
        s,
        max,
        "LOW_LIQUIDITY_COVERAGE",
        "Liquidity coverage evaluated",
        Map.of("coverageRatio", ratio, "maturityDebt", due),
        m.getId());
  }

  private static int eventScore(CreditEventType t) {
    return switch (t) {
      case NEGATIVE_OUTLOOK -> 2;
      case CREDIT_RATING_DOWNGRADE -> 4;
      case SPECULATIVE_GRADE_ENTRY -> 7;
      case CREDIT_RATING_CCC_OR_BELOW -> 10;
      case FUNDING_FAILURE -> 8;
      case RIGHTS_OFFERING_FAILURE, BOND_ISSUANCE_FAILURE -> 8;
      case REFINANCING_FAILURE -> 12;
      case PAYMENT_DELAY -> 15;
      case EVENT_OF_DEFAULT, ACCELERATION_EVENT -> 18;
      case PAYMENT_DEFAULT, CREDIT_RATING_D, REHABILITATION_FILED, BANKRUPTCY_FILED -> 25;
      default -> 5;
    };
  }

  private static boolean isDefaultEvent(CreditEventType t) {
    return switch (t) {
      case EVENT_OF_DEFAULT,
              ACCELERATION_EVENT,
              PAYMENT_DEFAULT,
              CREDIT_RATING_D,
              REHABILITATION_FILED,
              BANKRUPTCY_FILED ->
          true;
      default -> false;
    };
  }

  private static List<DebtMaturity> future(RiskEvaluationContext c) {
    return c.debts().stream().filter(d -> !d.getMaturityDate().isBefore(c.asOfDate())).toList();
  }

  private static long days(RiskEvaluationContext c, DebtMaturity d) {
    return ChronoUnit.DAYS.between(c.asOfDate(), d.getMaturityDate());
  }

  private static BigDecimal sum(List<DebtMaturity> ds) {
    return ds.stream().map(DebtMaturity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static boolean shortTerm(RiskEvaluationContext c, DebtMaturity d) {
    return d.isShortTerm()
        || switch (d.getDebtType()) {
          case CP, ABSTB, SHORT_TERM_BORROWING, CURRENT_PORTION_LONG_TERM_DEBT -> true;
          default -> days(c, d) <= 365;
        };
  }

  private interface Eval {
    RiskRuleResult get(RiskEvaluationContext c);
  }

  private static RiskRule rule(int p, RiskRuleType t, boolean req, Eval e) {
    return new RiskRule() {
      public int priority() {
        return p;
      }

      public RiskRuleType supports() {
        return t;
      }

      public boolean required() {
        return req;
      }

      public RiskRuleResult evaluate(RiskEvaluationContext c) {
        return e.get(c);
      }
    };
  }

  private static RiskRuleResult na(RiskRuleType t, RiskCategory c, int max, String m) {
    return RiskRuleResult.unavailable(t, c, max, m);
  }

  private static RiskRuleResult ok(
      RiskRuleType t,
      RiskCategory c,
      int s,
      int max,
      String signal,
      String message,
      Map<String, Object> evidence,
      Long source) {
    return RiskRuleResult.calculated(t, c, s, max, signal, message, evidence, source);
  }
}
