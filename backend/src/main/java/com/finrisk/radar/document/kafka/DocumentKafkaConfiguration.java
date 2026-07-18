package com.finrisk.radar.document.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class DocumentKafkaConfiguration {
  @Bean
  NewTopic documentFetchRequested() {
    return topic(DocumentTopics.FETCH_REQUESTED);
  }

  @Bean
  NewTopic documentCollected() {
    return topic(DocumentTopics.COLLECTED);
  }

  @Bean
  NewTopic documentFailed() {
    return topic(DocumentTopics.FAILED);
  }

  @Bean
  NewTopic documentRiskAnalyzed() {
    return topic(DocumentTopics.RISK_ANALYZED);
  }

  private NewTopic topic(String n) {
    return TopicBuilder.name(n).partitions(1).replicas(1).build();
  }
}
