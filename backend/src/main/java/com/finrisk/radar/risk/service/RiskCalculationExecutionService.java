package com.finrisk.radar.risk.service;

import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.engine.*;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.stereotype.Service;

@Service
public class RiskCalculationExecutionService {
  private static final Logger log = LoggerFactory.getLogger(RiskCalculationExecutionService.class);
  private final RiskCalculationJobService jobs;
  private final RiskEvaluationContextFactory contexts;
  private final RiskScoringEngine engine;
  private final RiskResultPersistenceService persistence;

  public RiskCalculationExecutionService(
      RiskCalculationJobService jobs,
      RiskEvaluationContextFactory contexts,
      RiskScoringEngine engine,
      RiskResultPersistenceService persistence) {
    this.jobs = jobs;
    this.contexts = contexts;
    this.engine = engine;
    this.persistence = persistence;
  }

  public void execute(UUID id) {
    RiskCalculationJob current = jobs.get(id);
    if (current.getStatus() == RiskCalculationStatus.REQUESTED && !jobs.markRunning(id)) return;
    if (current.getStatus() == RiskCalculationStatus.COMPLETED
        || current.getStatus() == RiskCalculationStatus.FAILED) return;
    long start = System.nanoTime();
    RiskCalculationJob job = jobs.get(id);
    try {
      RiskEvaluationContext context = contexts.create(job.getAssetId(), job.getDataAsOfDate());
      RiskCalculationOutcome out = engine.calculate(id, context, job.getRuleVersion());
      RiskScore score = persistence.persist(job, context, out);
      RiskJobExecutionSummary s = out.summary();
      log.info(
          "event=risk_calculation_job_completed jobId={} assetId={} elapsedMs={}"
              + " executedRuleCount={} calculatedRuleCount={} notAvailableRuleCount={}"
              + " insufficientDataRuleCount={} failedRuleCount={} ruleVersion={} totalScore={}"
              + " riskGrade={} dataQuality={} confidence={}",
          id,
          job.getAssetId(),
          elapsed(start),
          s.executed(),
          s.calculated(),
          s.unavailable(),
          s.insufficient(),
          s.failed(),
          job.getRuleVersion(),
          score.getTotalScore(),
          score.getRiskGrade(),
          score.getDataQuality(),
          score.getConfidence());
    } catch (RuntimeException exception) {
      log.error(
          "event=risk_calculation_job_attempt_failed jobId={} assetId={} elapsedMs={}"
              + " executedRuleCount={} calculatedRuleCount={} notAvailableRuleCount={}"
              + " insufficientDataRuleCount={} failedRuleCount={} failedRule={} ruleVersion={}",
          id,
          job.getAssetId(),
          elapsed(start),
          0,
          0,
          0,
          0,
          1,
          "unknown",
          job.getRuleVersion(),
          exception);
      throw exception;
    }
  }

  private long elapsed(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }
}
