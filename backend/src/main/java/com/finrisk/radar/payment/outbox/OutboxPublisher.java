package com.finrisk.radar.payment.outbox;

import com.finrisk.radar.payment.PaymentProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class OutboxPublisher {
  private final OutboxRepository events;
  private final KafkaTemplate<String, Object> kafka;
  private final PaymentProperties properties;
  private final MeterRegistry meters;

  OutboxPublisher(
      OutboxRepository events,
      KafkaTemplate<String, Object> kafka,
      PaymentProperties properties,
      MeterRegistry meters) {
    this.events = events;
    this.kafka = kafka;
    this.properties = properties;
    this.meters = meters;
  }

  @Scheduled(fixedDelayString = "${app.payment.outbox.fixed-delay:1s}")
  @Transactional
  public void publish() {
    for (OutboxEvent event :
        events.findByStatusOrderByOccurredAtAsc(
            OutboxStatus.PENDING, PageRequest.of(0, properties.outbox().batchSize()))) {
      try {
        PaymentDomainEvent payload = event.event();
        kafka
            .send(PaymentEventConfiguration.TOPIC, event.getId().toString(), payload)
            .get(5, TimeUnit.SECONDS);
        event.published();
        meters.counter("outbox.publish.success").increment();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        event.failed("Interrupted", properties.outbox().maxAttempts());
        meters.counter("outbox.publish.failed").increment();
      } catch (Exception exception) {
        event.failed(exception.getMessage(), properties.outbox().maxAttempts());
        meters.counter("outbox.publish.failed").increment();
      }
    }
  }
}
