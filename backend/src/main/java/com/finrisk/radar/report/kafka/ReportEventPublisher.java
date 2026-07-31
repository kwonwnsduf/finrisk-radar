package com.finrisk.radar.report.kafka;

import com.finrisk.radar.report.event.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReportEventPublisher {
  private final KafkaTemplate<String, Object> kafka;
  private final MeterRegistry meters;
  private static final Logger log = LoggerFactory.getLogger(ReportEventPublisher.class);

  public ReportEventPublisher(KafkaTemplate<String, Object> kafka, MeterRegistry meters) {
    this.kafka = kafka;
    this.meters = meters;
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

  public void completed(ReportCompletedEvent event) {
    outcome(ReportTopics.COMPLETED, event.reportId().toString(), event);
  }

  public void failed(ReportFailedEvent event) {
    outcome(ReportTopics.FAILED, event.reportId().toString(), event);
  }

  private void outcome(String topic, String key, Object event) {
    try {
      kafka.send(topic, key, event).whenComplete((result, error) -> {
        if (error != null) recordFailure(topic, error);
      });
    } catch (RuntimeException exception) {
      recordFailure(topic, exception);
    }
  }

  private void recordFailure(String topic, Throwable error) {
    log.error("event=notification_source_publish_failed source=report topic={}", topic, error);
    meters.counter("notification.event.publish.failure", "source", "report", "topic", topic)
        .increment();
  }
}
