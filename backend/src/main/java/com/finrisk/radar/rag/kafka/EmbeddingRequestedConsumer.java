package com.finrisk.radar.rag.kafka;

import com.finrisk.radar.rag.event.EmbeddingRequestedEvent;
import com.finrisk.radar.rag.service.DocumentEmbeddingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingRequestedConsumer {
  private final DocumentEmbeddingService embeddings;

  public EmbeddingRequestedConsumer(DocumentEmbeddingService embeddings) {
    this.embeddings = embeddings;
  }

  @KafkaListener(
      topics = RagTopics.EMBEDDING_REQUESTED,
      groupId = "finrisk-rag-embedding-worker",
      containerFactory = "ragKafkaListenerContainerFactory")
  public void consume(EmbeddingRequestedEvent event) {
    embeddings.process(event.jobId());
  }
}
