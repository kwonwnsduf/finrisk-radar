package com.finrisk.radar.collector.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class MarketDataKafkaConfiguration {
	@Bean NewTopic marketDataFetchRequestedTopic() { return topic(MarketDataTopics.FETCH_REQUESTED); }
	@Bean NewTopic marketDataFetchedTopic() { return topic(MarketDataTopics.FETCHED); }
	@Bean NewTopic collectionFailedTopic() { return topic(MarketDataTopics.COLLECTION_FAILED); }
	private NewTopic topic(String name) { return TopicBuilder.name(name).partitions(1).replicas(1).build(); }
}
