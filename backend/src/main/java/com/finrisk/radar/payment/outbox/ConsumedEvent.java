package com.finrisk.radar.payment.outbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consumed_events")
class ConsumedEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "consumer_name", nullable = false, length = 100)
  private String consumerName;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "consumed_at", nullable = false)
  private LocalDateTime consumedAt;

  protected ConsumedEvent() {}

  ConsumedEvent(String consumerName, UUID eventId) {
    this.consumerName = consumerName;
    this.eventId = eventId;
    this.consumedAt = LocalDateTime.now();
  }
}
