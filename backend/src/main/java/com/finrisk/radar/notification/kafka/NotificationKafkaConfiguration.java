package com.finrisk.radar.notification.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class NotificationKafkaConfiguration {
  public static final String GROUP_ID = "finrisk-notification-v1";

  @Bean("notificationKafkaListenerContainerFactory")
  ConcurrentKafkaListenerContainerFactory<String, Object> notificationFactory(
      ConsumerFactory<String, Object> consumers, MeterRegistry meters) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
    factory.setConsumerFactory(consumers);
    var backoff = new ExponentialBackOffWithMaxRetries(3);
    backoff.setInitialInterval(500);
    backoff.setMultiplier(2);
    backoff.setMaxInterval(4_000);
    var handler =
        new DefaultErrorHandler(
            (ConsumerRecord<?, ?> record, Exception exception) ->
                meters
                    .counter(
                        "notification.consumer.failure",
                        "source",
                        record.topic() == null ? "unknown" : record.topic())
                    .increment(),
            backoff);
    factory.setCommonErrorHandler(handler);
    return factory;
  }
}
