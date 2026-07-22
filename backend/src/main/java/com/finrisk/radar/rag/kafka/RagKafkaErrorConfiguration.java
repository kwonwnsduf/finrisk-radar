package com.finrisk.radar.rag.kafka;

import com.finrisk.radar.rag.embedding.EmbeddingClientException;
import com.finrisk.radar.rag.event.EmbeddingRequestedEvent;
import com.finrisk.radar.rag.service.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class RagKafkaErrorConfiguration {
  private static final Logger log = LoggerFactory.getLogger(RagKafkaErrorConfiguration.class);

  @Bean("ragKafkaListenerContainerFactory")
  ConcurrentKafkaListenerContainerFactory<String, Object> ragFactory(
      ConsumerFactory<String, Object> consumers, EmbeddingJobService jobs) {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumers);
    ExponentialBackOffWithMaxRetries backoff = new ExponentialBackOffWithMaxRetries(2);
    backoff.setInitialInterval(1000);
    backoff.setMultiplier(2);
    backoff.setMaxInterval(2000);
    DefaultErrorHandler handler =
        new DefaultErrorHandler((record, exception) -> recover(record, exception, jobs), backoff);
    handler.addNotRetryableExceptions(
        NonRetryableEmbeddingException.class,
        IllegalArgumentException.class,
        DataIntegrityViolationException.class);
    factory.setCommonErrorHandler(handler);
    return factory;
  }

  private void recover(ConsumerRecord<?, ?> record, Exception exception, EmbeddingJobService jobs) {
    if (record.value() instanceof EmbeddingRequestedEvent event) {
      Throwable cause = relevant(exception);
      String code =
          cause instanceof NonRetryableEmbeddingException nonRetryable
              ? nonRetryable.getCode()
              : cause instanceof EmbeddingClientException client
                  ? client.getCode()
                  : "RAG_EMBEDDING_FAILED";
      jobs.fail(event.jobId(), code, cause.getMessage());
      log.error(
          "event=rag_embedding_failed jobId={} documentId={} topic={} partition={} offset={}"
              + " failureCode={}",
          event.jobId(),
          event.documentId(),
          record.topic(),
          record.partition(),
          record.offset(),
          code,
          exception);
    }
  }

  private Throwable relevant(Throwable value) {
    Throwable current = value;
    while (current != null) {
      if (current instanceof NonRetryableEmbeddingException
          || current instanceof EmbeddingClientException) return current;
      current = current.getCause();
    }
    return value;
  }
}
