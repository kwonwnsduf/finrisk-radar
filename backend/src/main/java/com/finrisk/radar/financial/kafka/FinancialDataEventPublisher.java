package com.finrisk.radar.financial.kafka;

import com.finrisk.radar.financial.event.FinancialDataFetchFailedEvent;
import com.finrisk.radar.financial.event.FinancialDataFetchRequestedEvent;
import com.finrisk.radar.financial.event.FinancialDataFetchedEvent;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class FinancialDataEventPublisher {
  private final KafkaTemplate<String, Object> template;

  public FinancialDataEventPublisher(KafkaTemplate<String, Object> template) {
    this.template = template;
  }

  public void publishRequestedAndAwait(FinancialDataFetchRequestedEvent event) {
    sendAndAwait(FinancialDataTopics.FETCH_REQUESTED, event.assetId().toString(), event);
  }

  public void publishFetched(FinancialDataFetchedEvent event) {
    sendAndAwait(FinancialDataTopics.FETCHED, event.assetId().toString(), event);
  }

  public void publishFailed(FinancialDataFetchFailedEvent event) {
    sendAndAwait(FinancialDataTopics.FETCH_FAILED, event.assetId().toString(), event);
  }

  private void sendAndAwait(String topic, String key, Object event) {
    try {
      template.send(topic, key, event).get(5, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new FinancialDataEventPublishException(
          "Financial data request event could not be published.", exception);
    } catch (Exception exception) {
      throw new FinancialDataEventPublishException(
          "Financial data request event could not be published.", exception);
    }
  }
}
