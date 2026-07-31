package com.finrisk.radar.fsd;

import com.finrisk.radar.fsd.event.FsdReviewRequiredEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class FsdEventPublisher {
  private static final Logger log = LoggerFactory.getLogger(FsdEventPublisher.class);
  private final KafkaTemplate<String, Object> kafka;
  private final MeterRegistry meters;

  public FsdEventPublisher(KafkaTemplate<String, Object> kafka, MeterRegistry meters) {
    this.kafka = kafka;
    this.meters = meters;
  }

  public void reviewRequired(FsdReviewRequiredEvent event) {
    try {
      kafka
          .send(FsdTopics.REVIEW_REQUIRED, event.fsdEventId().toString(), event)
          .whenComplete(
              (result, error) -> {
                if (error != null) recordFailure(error);
              });
    } catch (RuntimeException exception) {
      recordFailure(exception);
    }
  }

  private void recordFailure(Throwable error) {
    log.error(
        "event=notification_source_publish_failed source=fsd topic={}",
        FsdTopics.REVIEW_REQUIRED,
        error);
    meters
        .counter(
            "notification.event.publish.failure",
            "source",
            "fsd",
            "topic",
            FsdTopics.REVIEW_REQUIRED)
        .increment();
  }
}
