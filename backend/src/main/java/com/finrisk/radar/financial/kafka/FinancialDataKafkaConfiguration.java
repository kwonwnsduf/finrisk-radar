package com.finrisk.radar.financial.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class FinancialDataKafkaConfiguration {
	@Bean NewTopic financialDataFetchRequestedTopic() { return topic(FinancialDataTopics.FETCH_REQUESTED); }
	@Bean NewTopic financialDataFetchedTopic() { return topic(FinancialDataTopics.FETCHED); }
	@Bean NewTopic financialDataFetchFailedTopic() { return topic(FinancialDataTopics.FETCH_FAILED); }
	private NewTopic topic(String name) { return TopicBuilder.name(name).partitions(1).replicas(1).build(); }
}
