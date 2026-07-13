package com.finrisk.radar.risk.kafka;

import com.finrisk.radar.risk.event.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class RiskEventPublisher {
  private static final Logger log = LoggerFactory.getLogger(RiskEventPublisher.class);
  private final KafkaTemplate<String, Object> template;

  public RiskEventPublisher(KafkaTemplate<String, Object> t) {
    template = t;
  }

  public void publishRequested(RiskScoreRequestedEvent e) {
    sendAwait(RiskTopics.REQUESTED, e.assetId().toString(), e);
  }

  public void publishCalculated(RiskScoreCalculatedEvent e) {
    sendAsync(RiskTopics.CALCULATED, e.assetId().toString(), e);
  }

  public void publishFailed(RiskScoreFailedEvent e) {
    sendAsync(RiskTopics.FAILED, e.assetId().toString(), e);
  }

  public void publishSignal(RiskSignalDetectedEvent e) {
    sendAsync(RiskTopics.SIGNAL_DETECTED, e.assetId().toString(), e);
  }

  private void sendAwait(String topic, String key, Object value) {
    Throwable last = null;
    for (int i = 0; i < 3; i++) {
      try {
        template.send(topic, key, value).get(5, TimeUnit.SECONDS);
        return;
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new RiskEventPublishException("Risk event publication interrupted", ex);
      } catch (Exception ex) {
        last = ex;
        if (i < 2) pause(1L << i);
      }
    }
    throw new RiskEventPublishException("Risk event could not be published", last);
  }

  private void sendAsync(String topic, String key, Object value) {
    long start = System.nanoTime();
    template
        .send(topic, key, value)
        .whenComplete(
            (r, e) -> {
              long ms = (System.nanoTime() - start) / 1_000_000;
              if (e == null)
                log.debug("event=risk_event_published topic={} elapsedMs={}", topic, ms);
              else log.error("event=risk_event_publish_failed topic={} elapsedMs={}", topic, ms, e);
            });
  }

  private void pause(long seconds) {
    try {
      Thread.sleep(seconds * 1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RiskEventPublishException("Risk retry interrupted", e);
    }
  }
}
