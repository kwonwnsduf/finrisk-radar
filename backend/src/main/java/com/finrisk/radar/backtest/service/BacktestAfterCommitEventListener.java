package com.finrisk.radar.backtest.service;

import com.finrisk.radar.backtest.BacktestStatus;
import com.finrisk.radar.backtest.event.*;
import com.finrisk.radar.backtest.kafka.BacktestEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class BacktestAfterCommitEventListener {
  private final BacktestEventPublisher publisher;

  public BacktestAfterCommitEventListener(BacktestEventPublisher publisher) {
    this.publisher = publisher;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void finished(BacktestFinishedNotification event) {
    if (event.status() == BacktestStatus.COMPLETED) {
      publisher.publishCompleted(
          new BacktestCompletedEvent(
              event.jobId(), event.userId(), event.assetId(), event.occurredAt()));
    } else if (event.status() == BacktestStatus.FAILED) {
      publisher.publishFailed(
          new BacktestFailedEvent(
              event.jobId(), event.userId(), event.assetId(), event.occurredAt()));
    }
  }
}
