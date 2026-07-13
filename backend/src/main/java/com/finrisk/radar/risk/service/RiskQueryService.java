package com.finrisk.radar.risk.service;

import com.finrisk.radar.global.error.*;
import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.api.*;
import com.finrisk.radar.user.Role;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskQueryService {
  private final RiskCalculationJobRepository jobs;
  private final RiskScoreRepository scores;
  private final RiskSignalRepository signals;

  public RiskQueryService(
      RiskCalculationJobRepository j, RiskScoreRepository s, RiskSignalRepository sig) {
    jobs = j;
    scores = s;
    signals = sig;
  }

  @Transactional(readOnly = true)
  public RiskJobResponse job(UUID id, Long user, Role role) {
    RiskCalculationJob j =
        jobs.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RISK_JOB_NOT_FOUND));
    if (role != Role.ROLE_ADMIN && !j.getUserId().equals(user))
      throw new BusinessException(ErrorCode.RISK_JOB_FORBIDDEN);
    return RiskJobResponse.from(j, scores.findByJobId(id).map(RiskScore::getId).orElse(null));
  }

  @Transactional(readOnly = true)
  public RiskScoreResponse score(Long id) {
    RiskScore s =
        scores
            .findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RISK_SCORE_NOT_FOUND));
    return response(s);
  }

  @Transactional(readOnly = true)
  public RiskScoreResponse latest(Long asset) {
    return response(
        scores
            .findFirstByAssetIdOrderByCalculatedAtDescIdDesc(asset)
            .orElseThrow(() -> new BusinessException(ErrorCode.RISK_SCORE_NOT_FOUND)));
  }

  @Transactional(readOnly = true)
  public List<RiskScoreResponse> history(Long asset) {
    return scores.findByAssetIdOrderByCalculatedAtDescIdDesc(asset).stream()
        .map(this::response)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<RiskSignalResponse> signalResponses(Long score) {
    if (!scores.existsById(score)) throw new BusinessException(ErrorCode.RISK_SCORE_NOT_FOUND);
    return signals.findByRiskScoreIdOrderByScoreDescIdAsc(score).stream()
        .map(RiskSignalResponse::from)
        .toList();
  }

  private RiskScoreResponse response(RiskScore s) {
    List<RiskSignal> all = signals.findByRiskScoreIdOrderByScoreDescIdAsc(s.getId());
    List<TopRiskFactorResponse> factors = new ArrayList<>();
    Set<String> rules = new HashSet<>();
    for (RiskSignal signal :
        all.stream()
            .sorted(
                Comparator.comparing(RiskSignal::getSeverity)
                    .reversed()
                    .thenComparing(RiskSignal::getScore, Comparator.reverseOrder())
                    .thenComparing(RiskSignal::getSignalType))
            .toList()) {
      if (signal.getScore() <= 0 || !rules.add(signal.getRuleType())) continue;
      factors.add(
          new TopRiskFactorResponse(
              factors.size() + 1,
              signal.getCategory(),
              signal.getSignalType(),
              signal.getSeverity(),
              signal.getScore(),
              signal.getMessage(),
              signal.getEvidence()));
      if (factors.size() == 5) break;
    }
    return RiskScoreResponse.from(s, factors);
  }
}
