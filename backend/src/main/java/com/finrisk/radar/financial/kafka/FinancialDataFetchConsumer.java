package com.finrisk.radar.financial.kafka;

import com.finrisk.radar.financial.FinancialCollectionLog;
import com.finrisk.radar.financial.FinancialCollectionLogService;
import com.finrisk.radar.financial.FinancialCollectionResult;
import com.finrisk.radar.financial.FinancialDataCollectionService;
import com.finrisk.radar.financial.event.FinancialDataFetchFailedEvent;
import com.finrisk.radar.financial.event.FinancialDataFetchRequestedEvent;
import com.finrisk.radar.financial.event.FinancialDataFetchedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FinancialDataFetchConsumer {
	private final FinancialDataCollectionService collections;
	private final FinancialCollectionLogService logs;
	private final FinancialDataEventPublisher publisher;

	public FinancialDataFetchConsumer(FinancialDataCollectionService collections, FinancialCollectionLogService logs,
			FinancialDataEventPublisher publisher) {
		this.collections = collections; this.logs = logs; this.publisher = publisher;
	}

	@KafkaListener(topics = FinancialDataTopics.FETCH_REQUESTED, groupId = "finrisk-financial-data-collector")
	public void consume(FinancialDataFetchRequestedEvent event) {
		try {
			FinancialCollectionResult result = collections.collect(event.jobId());
			if (result == null) return;
			publisher.publishFetched(new FinancialDataFetchedEvent(result.jobId(), result.assetId(), result.stockCode(),
					result.corpCode(), result.year(), result.quarter(), result.statementDivision(),
					result.fallbackUsed(), result.recordCount(), result.rawS3Path(), Instant.now()));
		} catch (RuntimeException exception) {
			FinancialCollectionLog log = logs.getInternal(event.jobId());
			publisher.publishFailed(new FinancialDataFetchFailedEvent(log.getJobId(), log.getAssetId(), log.getStockCode(),
					log.getYear(), log.getQuarter(), "FINANCIAL_DATA_COLLECTION_FAILED", log.getMessage(), Instant.now()));
		}
	}
}
