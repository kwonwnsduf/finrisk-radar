package com.finrisk.radar.payment.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class PaymentEventConfiguration {
  public static final String TOPIC = "finrisk.payment.events.v1";

  @Bean
  NewTopic paymentEventsTopic() {
    return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
  }
}
