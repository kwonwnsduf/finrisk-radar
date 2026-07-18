package com.finrisk.radar.document.kafka;

import com.finrisk.radar.document.event.*;
import com.finrisk.radar.document.service.DocumentRiskAnalysisService;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentRiskAnalysisWorker {
  private final DocumentRiskAnalysisService analysis;
  private final DocumentEventPublisher publisher;

  public DocumentRiskAnalysisWorker(
      DocumentRiskAnalysisService analysis, DocumentEventPublisher publisher) {
    this.analysis = analysis;
    this.publisher = publisher;
  }

  @KafkaListener(topics = DocumentTopics.COLLECTED, groupId = "finrisk-document-risk-analyzer")
  public void consume(DocumentCollectedEvent e) {
    var result = analysis.analyze(e.documentId());
    publisher.analyzed(
        new DocumentRiskAnalyzedEvent(
            1,
            e.correlationId(),
            e.documentId(),
            result.matchCount(),
            result.candidateCount(),
            Instant.now()));
  }
}
