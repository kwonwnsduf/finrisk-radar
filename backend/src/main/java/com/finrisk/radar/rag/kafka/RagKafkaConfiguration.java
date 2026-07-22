package com.finrisk.radar.rag.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class RagKafkaConfiguration {
  @Bean
  NewTopic ragEmbeddingRequested() {
    return TopicBuilder.name(RagTopics.EMBEDDING_REQUESTED).partitions(1).replicas(1).build();
  }
}
