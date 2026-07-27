package com.finrisk.radar.payment.outbox;

import com.finrisk.radar.global.error.BusinessException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
class PaymentKafkaErrorConfiguration {
  @Bean("paymentKafkaListenerContainerFactory")
  ConcurrentKafkaListenerContainerFactory<String, Object> paymentFactory(
      ConsumerFactory<String, Object> consumers) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
    factory.setConsumerFactory(consumers);
    var backoff = new ExponentialBackOffWithMaxRetries(3);
    backoff.setInitialInterval(500);
    backoff.setMultiplier(2);
    backoff.setMaxInterval(4_000);
    var handler = new DefaultErrorHandler(backoff);
    handler.addNotRetryableExceptions(BusinessException.class, IllegalArgumentException.class);
    factory.setCommonErrorHandler(handler);
    return factory;
  }
}
