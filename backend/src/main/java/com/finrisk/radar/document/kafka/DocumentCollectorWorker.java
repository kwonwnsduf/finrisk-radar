package com.finrisk.radar.document.kafka;

import com.finrisk.radar.document.event.*;
import com.finrisk.radar.document.service.DocumentCollectionExecutionService;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentCollectorWorker {
  private final DocumentCollectionExecutionService execution;
  private final DocumentEventPublisher publisher;

  public DocumentCollectorWorker(
      DocumentCollectionExecutionService execution, DocumentEventPublisher publisher) {
    this.execution = execution;
    this.publisher = publisher;
  }

  @KafkaListener(topics = DocumentTopics.FETCH_REQUESTED, groupId = "finrisk-document-collector")
  public void consume(DocumentFetchRequestedEvent e) {
    try {
      for (var result : execution.execute(e.jobId()))
        publisher.collected(
            new DocumentCollectedEvent(
                1,
                e.correlationId(),
                result.document().getId(),
                result.assetIds(),
                result.document().getContentVersion(),
                Instant.now()));
    } catch (RuntimeException ex) {
      publisher.failed(
          new DocumentCollectionFailedEvent(
              1,
              e.correlationId(),
              e.jobId(),
              e.assetId(),
              "DOCUMENT_COLLECTION_FAILED",
              ex.getMessage(),
              Instant.now()));
    }
  }
}
