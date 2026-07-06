package com.finrisk.radar.backtest.kafka;

import com.finrisk.radar.backtest.event.BacktestRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class BacktestEventPublisherTest {
	@Test
	void publishesUsingTheJobIdAsTheKafkaKey() {
		KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
		BacktestEventPublisher publisher = new BacktestEventPublisher(template);
		BacktestRequestedEvent event = new BacktestRequestedEvent(UUID.randomUUID(), Instant.now());
		when(template.send(BacktestTopics.REQUESTED, event.jobId().toString(), event))
				.thenReturn(CompletableFuture.completedFuture(null));

		publisher.publishRequestedAndAwait(event);

		verify(template).send(BacktestTopics.REQUESTED, event.jobId().toString(), event);
	}

	@Test
	void convertsBrokerFailuresToADomainPublisherException() {
		KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
		BacktestEventPublisher publisher = new BacktestEventPublisher(template);
		BacktestRequestedEvent event = new BacktestRequestedEvent(UUID.randomUUID(), Instant.now());
		CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
		failed.completeExceptionally(new RuntimeException("broker unavailable"));
		when(template.send(BacktestTopics.REQUESTED, event.jobId().toString(), event)).thenReturn(failed);

		assertThatThrownBy(() -> publisher.publishRequestedAndAwait(event))
				.isInstanceOf(BacktestEventPublishException.class);
	}
}
