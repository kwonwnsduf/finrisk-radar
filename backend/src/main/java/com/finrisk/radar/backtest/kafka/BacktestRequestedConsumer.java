package com.finrisk.radar.backtest.kafka;

import com.finrisk.radar.backtest.event.BacktestRequestedEvent;
import com.finrisk.radar.backtest.service.BacktestExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BacktestRequestedConsumer {
	private static final Logger log = LoggerFactory.getLogger(BacktestRequestedConsumer.class);
	private final BacktestExecutionService executions;

	public BacktestRequestedConsumer(BacktestExecutionService executions) { this.executions = executions; }

	@KafkaListener(topics = BacktestTopics.REQUESTED, groupId = "finrisk-backtest-worker")
	public void consume(BacktestRequestedEvent event) {
		try {
			executions.execute(event.jobId());
		} catch (RuntimeException exception) {
			log.error("Backtest job {} failed", event.jobId(), exception);
		}
	}
}
