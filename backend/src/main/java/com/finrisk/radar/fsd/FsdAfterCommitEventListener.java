package com.finrisk.radar.fsd;

import com.finrisk.radar.fsd.event.FsdReviewRequiredEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class FsdAfterCommitEventListener {
  private final FsdEventPublisher publisher;

  public FsdAfterCommitEventListener(FsdEventPublisher publisher) {
    this.publisher = publisher;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void detected(FsdDetectedNotification event) {
    publisher.reviewRequired(
        new FsdReviewRequiredEvent(
            event.fsdEventId(),
            event.paymentOrderId(),
            event.decision(),
            event.severity(),
            event.detectedAt()));
  }
}
