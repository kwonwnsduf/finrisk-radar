package com.finrisk.radar.fsd;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class FsdKafkaConfiguration {
  @Bean
  NewTopic fsdReviewRequiredTopic() {
    return TopicBuilder.name(FsdTopics.REVIEW_REQUIRED).partitions(1).replicas(1).build();
  }
}
