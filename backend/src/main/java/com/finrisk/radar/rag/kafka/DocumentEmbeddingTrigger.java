package com.finrisk.radar.rag.kafka;

import com.finrisk.radar.document.event.DocumentCollectedEvent;
import com.finrisk.radar.document.kafka.DocumentTopics;
import com.finrisk.radar.rag.service.EmbeddingRequestService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentEmbeddingTrigger {
  private final EmbeddingRequestService requests;

  public DocumentEmbeddingTrigger(EmbeddingRequestService requests) {
    this.requests = requests;
  }

  @KafkaListener(topics = DocumentTopics.COLLECTED, groupId = "finrisk-rag-embedding-trigger")
  public void consume(DocumentCollectedEvent event) {
    requests.request(event.documentId(), event.correlationId(), false);
  }
}
