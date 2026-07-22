package com.finrisk.radar.rag.kafka;

import com.finrisk.radar.rag.event.EmbeddingRequestedEvent;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class RagEventPublisher {
  private final KafkaTemplate<String, Object> kafka;

  public RagEventPublisher(KafkaTemplate<String, Object> kafka) {
    this.kafka = kafka;
  }

  public void requested(EmbeddingRequestedEvent event) {
    try {
      kafka
          .send(RagTopics.EMBEDDING_REQUESTED, event.documentId().toString(), event)
          .get(5, TimeUnit.SECONDS);
    } catch (Exception exception) {
      throw new IllegalStateException("RAG embedding event could not be published.", exception);
    }
  }
}
