package com.finrisk.radar.backtest.kafka;

import com.finrisk.radar.backtest.event.BacktestRequestedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class BacktestEventPublisher {
	private final KafkaTemplate<String, Object> template;

	public BacktestEventPublisher(KafkaTemplate<String, Object> template) { this.template = template; }

	public void publishRequestedAndAwait(BacktestRequestedEvent event) {
		try {
			template.send(BacktestTopics.REQUESTED, event.jobId().toString(), event).get(5, TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new BacktestEventPublishException("Backtest request event could not be published.", exception);
		} catch (Exception exception) {
			throw new BacktestEventPublishException("Backtest request event could not be published.", exception);
		}
	}
}
