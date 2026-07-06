package com.finrisk.radar.collector.kafka;

import com.finrisk.radar.collector.event.CollectionFailedEvent;
import com.finrisk.radar.collector.event.MarketDataFetchRequestedEvent;
import com.finrisk.radar.collector.event.MarketDataFetchedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class MarketDataEventPublisher {
	private final KafkaTemplate<String, Object> template;
	public MarketDataEventPublisher(KafkaTemplate<String, Object> template) { this.template = template; }
	public void publishRequestedAndAwait(MarketDataFetchRequestedEvent event) {
		try {
			template.send(MarketDataTopics.FETCH_REQUESTED, event.assetId().toString(), event).get(5, TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new EventPublishException("Collection request event could not be published.", exception);
		} catch (Exception exception) {
			throw new EventPublishException("Collection request event could not be published.", exception);
		}
	}
	public void publishFetched(MarketDataFetchedEvent event) {
		template.send(MarketDataTopics.FETCHED, event.assetId().toString(), event);
	}
	public void publishFailed(CollectionFailedEvent event) {
		template.send(MarketDataTopics.COLLECTION_FAILED, event.assetId().toString(), event);
	}
}
