package com.finrisk.radar.risk.engine;

import com.finrisk.radar.risk.*;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;

@Component
public class RiskScoringEngine {
  private static final Logger log = LoggerFactory.getLogger(RiskScoringEngine.class);
  private final RiskRuleRegistry registry;

  public RiskScoringEngine(RiskRuleRegistry registry) {
    this.registry = registry;
  }

  public RiskCalculationOutcome calculate(
      UUID jobId, RiskEvaluationContext context, String version) {
    List<RiskRuleResult> results = new ArrayList<>();
    RiskJobExecutionSummary summary = new RiskJobExecutionSummary();
    List<RiskRule> applicableRules = registry.rulesFor(context.asset().getAssetType());
    for (RiskRule rule : applicableRules) {
      long start = System.nanoTime();
      try {
        RiskRuleResult result = rule.evaluate(context);
        results.add(result);
        summary.record(result);
        long ms = elapsed(start);
        if (ms >= 100)
          log.info(
              "event=risk_rule_slow jobId={} assetId={} ruleVersion={} rule={} priority={}"
                  + " status={} score={} elapsedMs={}",
              jobId,
              context.asset().getId(),
              version,
              rule.supports(),
              rule.priority(),
              result.status(),
              result.score(),
              ms);
        else
          log.debug(
              "event=risk_rule_executed jobId={} assetId={} ruleVersion={} rule={} priority={}"
                  + " status={} score={} elapsedMs={}",
              jobId,
              context.asset().getId(),
              version,
              rule.supports(),
              rule.priority(),
              result.status(),
              result.score(),
              ms);
      } catch (RuntimeException ex) {
        summary.failed(rule.supports().name());
        log.error(
            "event=risk_rule_failed jobId={} assetId={} ruleVersion={} rule={} priority={}"
                + " elapsedMs={}",
            jobId,
            context.asset().getId(),
            version,
            rule.supports(),
            rule.priority(),
            elapsed(start),
            ex);
        throw ex;
      }
    }
    Map<RiskCategory, Integer> scores = new EnumMap<>(RiskCategory.class);
    Map<RiskCategory, CategoryCalculationStatus> statuses = new EnumMap<>(RiskCategory.class);
    List<String> missing = new ArrayList<>();
    for (RiskCategory c : RiskCategory.values()) {
      List<RiskRuleResult> category = results.stream().filter(r -> r.category() == c).toList();
      boolean any =
          category.stream().anyMatch(r -> r.status() == CategoryCalculationStatus.CALCULATED);
      boolean incomplete =
          category.stream().anyMatch(r -> r.status() != CategoryCalculationStatus.CALCULATED);
      if (!any) {
        statuses.put(c, CategoryCalculationStatus.NOT_AVAILABLE);
        missing.add(c.name());
      } else {
        statuses.put(
            c,
            incomplete
                ? CategoryCalculationStatus.INSUFFICIENT_DATA
                : CategoryCalculationStatus.CALCULATED);
        scores.put(
            c,
            Math.min(
                max(c),
                category.stream()
                    .filter(r -> r.status() == CategoryCalculationStatus.CALCULATED)
                    .mapToInt(RiskRuleResult::score)
                    .sum()));
        if (incomplete) missing.add(c.name());
      }
    }
    int required = (int) applicableRules.stream().filter(RiskRule::required).count();
    long calculated =
        results.stream()
            .filter(
                r ->
                    applicableRules.stream()
                            .anyMatch(rule -> rule.supports() == r.ruleType() && rule.required())
                        && r.status() == CategoryCalculationStatus.CALCULATED)
            .count();
    int rate = required == 0 ? 100 : (int) (calculated * 100 / required);
    RiskDataQuality quality = quality(context, rate);
    RiskConfidence confidence = confidence(quality, rate);
    DefaultStatus ds = defaultStatus(context.creditEvents());
    int total = scores.values().stream().mapToInt(Integer::intValue).sum();
    total = override(total, ds);
    return new RiskCalculationOutcome(
        Math.min(100, total),
        grade(total),
        ds,
        scores,
        statuses,
        quality,
        confidence,
        rate,
        List.copyOf(missing),
        List.copyOf(results),
        summary);
  }

  private RiskDataQuality quality(RiskEvaluationContext c, int rate) {
    if (c.asset().getAssetType() == com.finrisk.radar.asset.AssetType.REIT) {
      LocalDate period = c.latestReitMetric().getPeriod();
      if (period.isBefore(c.asOfDate().minusDays(370))) return RiskDataQuality.STALE;
      return rate == 100 ? RiskDataQuality.COMPLETE : RiskDataQuality.PARTIAL;
    }
    FinancialMetricLatest latest =
        new FinancialMetricLatest(c.latest().getYear(), c.latest().getQuarter());
    LocalDate approx =
        LocalDate.of(latest.year(), latest.quarter() * 3, 1)
            .withDayOfMonth(1)
            .plusMonths(1)
            .minusDays(1);
    if (approx.isBefore(c.asOfDate().minusDays(180))) return RiskDataQuality.STALE;
    return rate == 100 ? RiskDataQuality.COMPLETE : RiskDataQuality.PARTIAL;
  }

  private RiskConfidence confidence(RiskDataQuality q, int rate) {
    if (q == RiskDataQuality.STALE || rate < 100) return RiskConfidence.LOW;
    if (q == RiskDataQuality.COMPLETE) return RiskConfidence.HIGH;
    return RiskConfidence.MEDIUM;
  }

  private static int max(RiskCategory c) {
    return switch (c) {
      case FINANCIAL, CREDIT_EVENT -> 25;
      case LIQUIDITY_MATURITY -> 35;
      case MARKET -> 10;
      case GROUP_CONTAGION -> 5;
    };
  }

  private static RiskGrade grade(int score) {
    if (score >= 80) return RiskGrade.CRITICAL;
    if (score >= 60) return RiskGrade.HIGH;
    if (score >= 40) return RiskGrade.MEDIUM;
    if (score >= 20) return RiskGrade.CAUTION;
    return RiskGrade.LOW;
  }

  private static int override(int score, DefaultStatus s) {
    return switch (s) {
      case DEFAULT -> Math.max(score, 90);
      case REHABILITATION -> Math.max(score, 95);
      case BANKRUPTCY -> 100;
      default -> score;
    };
  }

  private static DefaultStatus defaultStatus(List<CreditEvent> events) {
    Set<CreditEventType> types = new HashSet<>();
    events.forEach(e -> types.add(e.getEventType()));
    if (types.contains(CreditEventType.BANKRUPTCY_FILED)) return DefaultStatus.BANKRUPTCY;
    if (types.contains(CreditEventType.REHABILITATION_FILED)) return DefaultStatus.REHABILITATION;
    if (types.contains(CreditEventType.PAYMENT_DEFAULT)
        || types.contains(CreditEventType.CREDIT_RATING_D)) return DefaultStatus.DEFAULT;
    if (types.contains(CreditEventType.EVENT_OF_DEFAULT)
        || types.contains(CreditEventType.ACCELERATION_EVENT))
      return DefaultStatus.EVENT_OF_DEFAULT;
    if (types.contains(CreditEventType.PAYMENT_DELAY)) return DefaultStatus.PAYMENT_DELAY;
    return DefaultStatus.NONE;
  }

  private static long elapsed(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }

  private record FinancialMetricLatest(int year, int quarter) {}
}
