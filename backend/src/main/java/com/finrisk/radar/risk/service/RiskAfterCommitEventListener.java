package com.finrisk.radar.risk.service;

import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.event.*;
import com.finrisk.radar.risk.kafka.RiskEventPublisher;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class RiskAfterCommitEventListener {
  private final RiskEventPublisher publisher;

  public RiskAfterCommitEventListener(RiskEventPublisher p) {
    publisher = p;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void completed(RiskCalculationCompletedNotification n) {
    RiskScore s = n.score();
    publisher.publishCalculated(
        new RiskScoreCalculatedEvent(
            n.jobId(),
            n.assetId(),
            s.getId(),
            s.getTotalScore(),
            s.getRiskGrade(),
            s.getDefaultStatus(),
            Instant.now()));
    n.signals().stream()
        .filter(
            x -> x.getSeverity() == RiskSeverity.HIGH || x.getSeverity() == RiskSeverity.CRITICAL)
        .forEach(
            x ->
                publisher.publishSignal(
                    new RiskSignalDetectedEvent(
                        n.jobId(),
                        n.assetId(),
                        s.getId(),
                        x.getId(),
                        x.getSignalType(),
                        x.getSeverity())));
  }
}
