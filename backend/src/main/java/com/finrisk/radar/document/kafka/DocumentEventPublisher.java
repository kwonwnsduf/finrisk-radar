package com.finrisk.radar.document.kafka;

import com.finrisk.radar.document.event.*;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DocumentEventPublisher {
  private final KafkaTemplate<String, Object> kafka;

  public DocumentEventPublisher(KafkaTemplate<String, Object> kafka) {
    this.kafka = kafka;
  }

  public void requested(DocumentFetchRequestedEvent e) {
    sendAwait(DocumentTopics.FETCH_REQUESTED, e.assetId().toString(), e);
  }

  public void collected(DocumentCollectedEvent e) {
    kafka.send(DocumentTopics.COLLECTED, e.documentId().toString(), e);
  }

  public void failed(DocumentCollectionFailedEvent e) {
    kafka.send(DocumentTopics.FAILED, e.assetId().toString(), e);
  }

  public void analyzed(DocumentRiskAnalyzedEvent e) {
    kafka.send(DocumentTopics.RISK_ANALYZED, e.documentId().toString(), e);
  }

  private void sendAwait(String topic, String key, Object event) {
    try {
      kafka.send(topic, key, event).get(5, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException("Document event could not be published.", e);
    }
  }
}
