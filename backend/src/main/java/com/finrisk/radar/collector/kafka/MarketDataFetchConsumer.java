package com.finrisk.radar.collector.kafka;

import com.finrisk.radar.collector.event.CollectionFailedEvent;
import com.finrisk.radar.collector.event.MarketDataFetchRequestedEvent;
import com.finrisk.radar.collector.event.MarketDataFetchedEvent;
import com.finrisk.radar.collector.log.CollectionLog;
import com.finrisk.radar.collector.log.CollectionLogService;
import com.finrisk.radar.collector.service.CollectionResult;
import com.finrisk.radar.collector.service.MarketPriceCollectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MarketDataFetchConsumer {
	private final MarketPriceCollectionService collections;
	private final CollectionLogService logs;
	private final MarketDataEventPublisher publisher;
	public MarketDataFetchConsumer(MarketPriceCollectionService collections, CollectionLogService logs,
			MarketDataEventPublisher publisher) {
		this.collections = collections; this.logs = logs; this.publisher = publisher;
	}
	@KafkaListener(topics = MarketDataTopics.FETCH_REQUESTED, groupId = "finrisk-market-data-collector")
	public void consume(MarketDataFetchRequestedEvent event) {
		try {
			CollectionResult result = collections.collect(event.jobId());
			if (result == null) return;
			publisher.publishFetched(new MarketDataFetchedEvent(result.jobId(), result.assetId(), result.ticker(),
					result.source(), result.recordCount(), result.rawS3Path(), Instant.now()));
		} catch (RuntimeException exception) {
			CollectionLog log = logs.getInternal(event.jobId());
			publisher.publishFailed(new CollectionFailedEvent(log.getJobId(), log.getAssetId(), log.getTicker(),
					"MARKET_DATA_COLLECTION_FAILED", log.getMessage(), Instant.now()));
		}
	}
}
