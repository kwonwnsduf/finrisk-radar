package com.finrisk.radar.backtest.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class BacktestKafkaConfiguration {
	@Bean NewTopic backtestRequestedTopic() {
		return TopicBuilder.name(BacktestTopics.REQUESTED).partitions(1).replicas(1).build();
	}
}
