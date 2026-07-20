package com.finrisk.radar.risk.service;

import com.finrisk.radar.global.error.*;
import com.finrisk.radar.risk.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class RiskCalculationJobService {
  private final RiskCalculationJobRepository jobs;

  public RiskCalculationJobService(RiskCalculationJobRepository j) {
    jobs = j;
  }

  @Transactional
  public RiskCalculationJob create(Long user, Long asset, String version) {
    return create(user, asset, version, false);
  }

  @Transactional
  public RiskCalculationJob create(Long user, Long asset, String version, boolean collecting) {
    if (jobs.existsByAssetIdAndStatusIn(
        asset,
        List.of(
            RiskCalculationStatus.COLLECTING,
            RiskCalculationStatus.REQUESTED,
            RiskCalculationStatus.RUNNING)))
      throw new BusinessException(ErrorCode.RISK_CALCULATION_ALREADY_RUNNING);
    return jobs.save(
        collecting
            ? RiskCalculationJob.collecting(user, asset, version, LocalDate.now())
            : RiskCalculationJob.requested(user, asset, version, LocalDate.now()));
  }

  @Transactional
  public boolean markRunning(UUID id) {
    return jobs.markRunning(id, LocalDateTime.now()) == 1;
  }

  @Transactional
  public boolean markRequested(UUID id) {
    return jobs.markRequested(id, LocalDateTime.now()) == 1;
  }

  @Transactional(readOnly = true)
  public RiskCalculationJob get(UUID id) {
    return jobs.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RISK_JOB_NOT_FOUND));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void fail(UUID id, String code, String message) {
    get(id).fail(code, message);
  }
}
