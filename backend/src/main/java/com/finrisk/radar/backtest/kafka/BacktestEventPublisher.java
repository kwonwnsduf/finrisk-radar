package com.finrisk.radar.backtest.kafka;

import com.finrisk.radar.backtest.event.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class BacktestEventPublisher {
	private final KafkaTemplate<String, Object> template;
	private final MeterRegistry meters;
	private static final Logger log = LoggerFactory.getLogger(BacktestEventPublisher.class);

	public BacktestEventPublisher(KafkaTemplate<String, Object> template, MeterRegistry meters) {
		this.template = template;
		this.meters = meters;
	}

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

	public void publishCompleted(BacktestCompletedEvent event) {
		publishOutcome(BacktestTopics.COMPLETED, event.jobId().toString(), event);
	}

	public void publishFailed(BacktestFailedEvent event) {
		publishOutcome(BacktestTopics.FAILED, event.jobId().toString(), event);
	}

	private void publishOutcome(String topic, String key, Object event) {
		try {
			template.send(topic, key, event).whenComplete((result, error) -> {
				if (error != null) recordOutcomeFailure(topic, error);
			});
		} catch (RuntimeException exception) {
			recordOutcomeFailure(topic, exception);
		}
	}

	private void recordOutcomeFailure(String topic, Throwable error) {
		log.error("event=notification_source_publish_failed source=backtest topic={}", topic, error);
		meters.counter("notification.event.publish.failure", "source", "backtest", "topic", topic).increment();
	}
}
