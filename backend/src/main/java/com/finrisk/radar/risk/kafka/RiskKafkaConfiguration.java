package com.finrisk.radar.risk.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class RiskKafkaConfiguration {
  @Bean
  NewTopic riskRequested() {
    return topic(RiskTopics.REQUESTED);
  }

  @Bean
  NewTopic riskCalculated() {
    return topic(RiskTopics.CALCULATED);
  }

  @Bean
  NewTopic riskFailed() {
    return topic(RiskTopics.FAILED);
  }

  @Bean
  NewTopic riskSignal() {
    return topic(RiskTopics.SIGNAL_DETECTED);
  }

  private NewTopic topic(String n) {
    return TopicBuilder.name(n).partitions(1).replicas(1).build();
  }
}
