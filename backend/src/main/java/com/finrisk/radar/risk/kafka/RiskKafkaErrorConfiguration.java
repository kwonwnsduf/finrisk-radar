package com.finrisk.radar.risk.kafka;

import com.finrisk.radar.global.error.*;
import com.finrisk.radar.risk.event.*;
import com.finrisk.radar.risk.service.RiskCalculationJobService;
import java.time.Instant;
import org.apache.kafka.clients.consumer.*;
import org.slf4j.*;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class RiskKafkaErrorConfiguration {
  private static final Logger log = LoggerFactory.getLogger(RiskKafkaErrorConfiguration.class);

  @Bean("riskKafkaListenerContainerFactory")
  ConcurrentKafkaListenerContainerFactory<String, Object> riskFactory(
      ConsumerFactory<String, Object> consumers,
      RiskCalculationJobService jobs,
      RiskEventPublisher publisher) {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumers);
    ExponentialBackOffWithMaxRetries backoff = new ExponentialBackOffWithMaxRetries(2);
    backoff.setInitialInterval(1000);
    backoff.setMultiplier(2);
    backoff.setMaxInterval(2000);
    DefaultErrorHandler handler =
        new DefaultErrorHandler(
            (record, exception) -> recover(record, exception, jobs, publisher), backoff);
    handler.addNotRetryableExceptions(
        BusinessException.class,
        IllegalArgumentException.class,
        ArithmeticException.class,
        IllegalStateException.class);
    factory.setCommonErrorHandler(handler);
    return factory;
  }

  private void recover(
      ConsumerRecord<?, ?> record,
      Exception exception,
      RiskCalculationJobService jobs,
      RiskEventPublisher publisher) {
    if (record.value() instanceof RiskScoreRequestedEvent event) {
      BusinessException business = findBusinessException(exception);
      String code =
          business == null
              ? ErrorCode.RISK_CALCULATION_FAILED.getCode()
              : business.getErrorCode().getCode();
      String message =
          business == null
              ? ErrorCode.RISK_CALCULATION_FAILED.getMessage()
              : business.getErrorCode().getMessage();
      jobs.fail(event.jobId(), code, message);
      publisher.publishFailed(
          new RiskScoreFailedEvent(event.jobId(), event.assetId(), code, message, Instant.now()));
      log.error(
          "event=risk_calculation_job_failed jobId={} assetId={} topic={} partition={} offset={}"
              + " failureCode={}",
          event.jobId(),
          event.assetId(),
          record.topic(),
          record.partition(),
          record.offset(),
          code,
          exception);
    } else
      log.error(
          "event=risk_message_recovery_failed topic={} partition={} offset={}",
          record.topic(),
          record.partition(),
          record.offset(),
          exception);
  }

  private BusinessException findBusinessException(Throwable exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof BusinessException business) return business;
      current = current.getCause();
    }
    return null;
  }
}
