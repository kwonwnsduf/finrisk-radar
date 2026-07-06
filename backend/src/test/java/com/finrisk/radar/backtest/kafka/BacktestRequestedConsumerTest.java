package com.finrisk.radar.backtest.kafka;

import com.finrisk.radar.backtest.event.BacktestRequestedEvent;
import com.finrisk.radar.backtest.service.BacktestExecutionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

class BacktestRequestedConsumerTest {
	@Test
	void delegatesTheJobToTheExecutionService() {
		BacktestExecutionService executions = mock(BacktestExecutionService.class);
		BacktestRequestedConsumer consumer = new BacktestRequestedConsumer(executions);
		BacktestRequestedEvent event = new BacktestRequestedEvent(UUID.randomUUID(), Instant.now());

		consumer.consume(event);

		verify(executions).execute(event.jobId());
	}

	@Test
	void catchesWorkerFailuresSoTheJobStatusRemainsTheResultSource() {
		BacktestExecutionService executions = mock(BacktestExecutionService.class);
		BacktestRequestedConsumer consumer = new BacktestRequestedConsumer(executions);
		BacktestRequestedEvent event = new BacktestRequestedEvent(UUID.randomUUID(), Instant.now());
		doThrow(new RuntimeException("failed")).when(executions).execute(event.jobId());

		consumer.consume(event);

		verify(executions).execute(event.jobId());
	}
}
