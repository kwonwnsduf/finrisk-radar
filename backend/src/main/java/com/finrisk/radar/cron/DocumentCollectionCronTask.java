package com.finrisk.radar.cron;

import com.finrisk.radar.document.service.DocumentCollectionBatchService;
import org.springframework.stereotype.Component;

@Component
public class DocumentCollectionCronTask implements Day20CronTask {
  private final DocumentCollectionBatchService batch;

  public DocumentCollectionCronTask(DocumentCollectionBatchService batch) {
    this.batch = batch;
  }

  @Override
  public String name() {
    return "document-collection";
  }

  @Override
  public void run() {
    DocumentCollectionBatchService.Result result = batch.collect();
    if (result.failed() > 0) {
      throw new IllegalStateException("Document collection failed for " + result.failed() + " asset(s).");
    }
  }
}
