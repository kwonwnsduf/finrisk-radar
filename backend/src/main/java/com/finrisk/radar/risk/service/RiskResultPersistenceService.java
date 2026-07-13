package com.finrisk.radar.risk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.engine.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskResultPersistenceService {
  private final RiskCalculationJobRepository jobs;
  private final RiskScoreRepository scores;
  private final RiskSignalRepository signals;
  private final ObjectMapper mapper;
  private final ApplicationEventPublisher events;

  public RiskResultPersistenceService(
      RiskCalculationJobRepository j,
      RiskScoreRepository s,
      RiskSignalRepository sig,
      ObjectMapper m,
      ApplicationEventPublisher e) {
    jobs = j;
    scores = s;
    signals = sig;
    mapper = m;
    events = e;
  }

  @Transactional
  public RiskScore persist(
      RiskCalculationJob job, RiskEvaluationContext context, RiskCalculationOutcome o) {
    RiskScore score =
        scores.saveAndFlush(
            RiskScore.create(
                job.getJobId(),
                job.getAssetId(),
                o.totalScore(),
                o.riskGrade(),
                o.defaultStatus(),
                o.scores().get(RiskCategory.FINANCIAL),
                o.scores().get(RiskCategory.LIQUIDITY_MATURITY),
                o.scores().get(RiskCategory.MARKET),
                o.scores().get(RiskCategory.CREDIT_EVENT),
                o.scores().get(RiskCategory.GROUP_CONTAGION),
                json(o.statuses()),
                o.dataQuality(),
                o.confidence(),
                o.requiredRuleSuccessRate(),
                json(o.missingCategories()),
                context.financials().size(),
                context.debts().size(),
                context.prices().size(),
                context.creditEvents().size(),
                context.relationships().size(),
                job.getDataAsOfDate(),
                job.getRuleVersion()));
    List<RiskSignal> saved = new ArrayList<>();
    for (RiskRuleResult r : o.results()) {
      if (r.status() != CategoryCalculationStatus.CALCULATED || r.score() <= 0) continue;
      saved.add(
          RiskSignal.create(
              score.getId(),
              job.getAssetId(),
              r.category(),
              r.ruleType().name(),
              r.signalType(),
              r.severity(),
              r.score(),
              r.message(),
              json(r.evidence()),
              r.category().name(),
              r.sourceId(),
              score.getId()
                  + ":"
                  + r.ruleType()
                  + ":"
                  + (r.sourceId() == null ? "policy" : r.sourceId())));
    }
    saved = signals.saveAllAndFlush(saved);
    jobs.findById(job.getJobId()).orElseThrow().complete();
    events.publishEvent(
        new RiskCalculationCompletedNotification(
            job.getJobId(), job.getAssetId(), score, List.copyOf(saved)));
    return score;
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Risk evidence could not be serialized", e);
    }
  }
}
