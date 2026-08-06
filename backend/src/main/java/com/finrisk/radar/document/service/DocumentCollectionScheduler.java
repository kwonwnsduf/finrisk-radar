package com.finrisk.radar.document.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.documents.scheduler", name = "enabled", havingValue = "true")
public class DocumentCollectionScheduler {
  private final DocumentCollectionBatchService batch;

  public DocumentCollectionScheduler(DocumentCollectionBatchService batch) {
    this.batch = batch;
  }

  @Scheduled(cron = "${app.documents.scheduler.cron:0 0 * * * *}")
  public void collect() {
    batch.collect();
  }
}
