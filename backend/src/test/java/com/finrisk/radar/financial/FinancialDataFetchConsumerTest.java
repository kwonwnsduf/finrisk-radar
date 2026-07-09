package com.finrisk.radar.financial;

import com.finrisk.radar.financial.event.FinancialDataFetchRequestedEvent;
import com.finrisk.radar.financial.kafka.FinancialDataEventPublisher;
import com.finrisk.radar.financial.kafka.FinancialDataFetchConsumer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinancialDataFetchConsumerTest {
	@Test void publishesFetchedEventAfterCollectionCompletes() {
		FinancialDataCollectionService collections = mock(FinancialDataCollectionService.class);
		FinancialCollectionLogService logs = mock(FinancialCollectionLogService.class);
		FinancialDataEventPublisher publisher = mock(FinancialDataEventPublisher.class);
		FinancialDataFetchConsumer consumer = new FinancialDataFetchConsumer(collections, logs, publisher);
		UUID jobId = UUID.randomUUID();
		when(collections.collect(jobId)).thenReturn(new FinancialCollectionResult(jobId, 1L, "005930", "00126380",
				2024, 4, "CFS", false, 1, "s3://bucket/raw.json"));

		consumer.consume(new FinancialDataFetchRequestedEvent(jobId, 1L, "005930", 2024, 4, Instant.now()));

		verify(publisher).publishFetched(any());
		verify(publisher, never()).publishFailed(any());
	}
}
