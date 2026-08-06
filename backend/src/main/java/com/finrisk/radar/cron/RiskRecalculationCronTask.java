package com.finrisk.radar.cron;

import com.finrisk.radar.document.service.DocumentRiskRecalculationCoordinator;
import org.springframework.stereotype.Component;

@Component
public class RiskRecalculationCronTask implements Day20CronTask {
  private final DocumentRiskRecalculationCoordinator coordinator;

  public RiskRecalculationCronTask(DocumentRiskRecalculationCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  @Override
  public String name() {
    return "risk-recalculation";
  }

  @Override
  public void run() {
    coordinator.retry();
  }
}
