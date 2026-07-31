package com.finrisk.radar.report.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class ReportKafkaConfiguration {
  @Bean
  NewTopic reportGenerationRequestedTopic() {
    return TopicBuilder.name(ReportTopics.GENERATION_REQUESTED).partitions(1).replicas(1).build();
  }

  @Bean
  NewTopic reportCompletedTopic() {
    return TopicBuilder.name(ReportTopics.COMPLETED).partitions(1).replicas(1).build();
  }

  @Bean
  NewTopic reportFailedTopic() {
    return TopicBuilder.name(ReportTopics.FAILED).partitions(1).replicas(1).build();
  }
}
