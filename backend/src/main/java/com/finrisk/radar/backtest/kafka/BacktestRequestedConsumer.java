package com.finrisk.radar.backtest.kafka;

import com.finrisk.radar.backtest.event.BacktestRequestedEvent;
import com.finrisk.radar.backtest.service.BacktestExecutionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BacktestRequestedConsumer {
	private static final Logger log = LoggerFactory.getLogger(BacktestRequestedConsumer.class);
	private final BacktestExecutionService executions;
	private final MeterRegistry meters;

	public BacktestRequestedConsumer(BacktestExecutionService executions, MeterRegistry meters) {
		this.executions = executions;
		this.meters = meters;
	}

	@KafkaListener(topics = BacktestTopics.REQUESTED, groupId = "finrisk-backtest-worker")
	public void consume(BacktestRequestedEvent event) {
		Timer.Sample sample = Timer.start(meters);
		try {
			executions.execute(event.jobId());
			meters.counter("worker.execution", "domain", "backtest", "outcome", "success").increment();
		} catch (RuntimeException exception) {
			meters.counter("worker.execution", "domain", "backtest", "outcome", "failure").increment();
			log.error("Backtest job {} failed", event.jobId(), exception);
		} finally {
			sample.stop(meters.timer("worker.execution.duration", "domain", "backtest"));
		}
	}
}
