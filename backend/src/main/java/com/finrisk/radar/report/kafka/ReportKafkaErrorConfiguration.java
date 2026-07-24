package com.finrisk.radar.report.kafka;

import com.finrisk.radar.report.event.ReportGenerationRequestedEvent;
import com.finrisk.radar.report.service.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class ReportKafkaErrorConfiguration {
  @Bean("reportKafkaListenerContainerFactory")
  ConcurrentKafkaListenerContainerFactory<String, Object> factory(
      ConsumerFactory<String, Object> consumers, ReportPersistenceService persistence) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
    factory.setConsumerFactory(consumers);
    var backoff = new ExponentialBackOffWithMaxRetries(2);
    backoff.setInitialInterval(1000);
    backoff.setMultiplier(2);
    backoff.setMaxInterval(2000);
    var handler =
        new DefaultErrorHandler(
            (record, exception) -> recover(record, exception, persistence), backoff);
    handler.addNotRetryableExceptions(NonRetryableReportException.class);
    factory.setCommonErrorHandler(handler);
    return factory;
  }

  private void recover(
      ConsumerRecord<?, ?> record, Exception exception, ReportPersistenceService persistence) {
    if (record.value() instanceof ReportGenerationRequestedEvent event) {
      ReportGenerationException relevant = find(exception);
      persistence.fail(
          event.reportId(),
          relevant == null ? "REPORT_GENERATION_FAILED" : relevant.getCode(),
          relevant == null ? "Report generation failed." : relevant.getMessage(),
          relevant != null && relevant.isRetryable());
    }
  }

  private ReportGenerationException find(Throwable value) {
    for (Throwable c = value; c != null; c = c.getCause())
      if (c instanceof ReportGenerationException r) return r;
    return null;
  }
}
