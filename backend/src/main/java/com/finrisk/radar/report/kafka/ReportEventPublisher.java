package com.finrisk.radar.report.kafka;

import com.finrisk.radar.report.event.ReportGenerationRequestedEvent;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReportEventPublisher {
  private final KafkaTemplate<String, Object> kafka;

  public ReportEventPublisher(KafkaTemplate<String, Object> kafka) {
    this.kafka = kafka;
  }

  public void requested(ReportGenerationRequestedEvent event) {
    try {
      kafka
          .send(ReportTopics.GENERATION_REQUESTED, event.reportId().toString(), event)
          .get(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ReportEventPublishException("Report event publish interrupted.", e);
    } catch (Exception e) {
      throw new ReportEventPublishException("Report event publish failed.", e);
    }
  }
}
