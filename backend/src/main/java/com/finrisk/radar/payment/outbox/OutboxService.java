package com.finrisk.radar.payment.outbox;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {
  private final OutboxRepository events;

  public OutboxService(OutboxRepository events) {
    this.events = events;
  }

  public void append(String aggregateId, String type, Map<String, Object> payload) {
    String key = aggregateId + ":" + type + ":v1";
    if (!events.existsByEventKey(key)) {
      events.save(OutboxEvent.pending(key, aggregateId, type, payload));
    }
  }
}
