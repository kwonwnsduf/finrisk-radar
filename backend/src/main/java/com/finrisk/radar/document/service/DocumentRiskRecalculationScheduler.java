package com.finrisk.radar.document.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "app.documents.recalculation-scheduler",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DocumentRiskRecalculationScheduler {
  private final DocumentRiskRecalculationCoordinator coordinator;

  public DocumentRiskRecalculationScheduler(DocumentRiskRecalculationCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  @Scheduled(fixedDelayString = "${app.documents.recalculation-retry-delay:60000}")
  public void retry() {
    coordinator.retry();
  }
}
