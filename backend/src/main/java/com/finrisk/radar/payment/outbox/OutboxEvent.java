package com.finrisk.radar.payment.outbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
class OutboxEvent {
  @Id private UUID id;

  @Column(name = "event_key", nullable = false, unique = true, length = 255)
  private String eventKey;

  @Column(name = "aggregate_type", nullable = false, length = 80)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, length = 100)
  private String aggregateId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OutboxStatus status;

  @Column(name = "occurred_at", nullable = false)
  private LocalDateTime occurredAt;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  protected OutboxEvent() {}

  static OutboxEvent pending(
      String key, String aggregateId, String type, Map<String, Object> payload) {
    OutboxEvent value = new OutboxEvent();
    value.id = UUID.randomUUID();
    value.eventKey = key;
    value.aggregateType = "PAYMENT_ORDER";
    value.aggregateId = aggregateId;
    value.eventType = type;
    value.payload = payload;
    value.status = OutboxStatus.PENDING;
    value.occurredAt = LocalDateTime.now();
    return value;
  }

  void published() {
    status = OutboxStatus.PUBLISHED;
    publishedAt = LocalDateTime.now();
    lastError = null;
  }

  void failed(String error, int maxAttempts) {
    attemptCount++;
    lastError =
        error == null
            ? "Unknown publish error"
            : error.substring(0, Math.min(error.length(), 1000));
    status = attemptCount >= maxAttempts ? OutboxStatus.FAILED : OutboxStatus.PENDING;
  }

  PaymentDomainEvent event() {
    return new PaymentDomainEvent(id, eventType, occurredAt, payload, 1);
  }

  public UUID getId() {
    return id;
  }
}

enum OutboxStatus {
  PENDING,
  PUBLISHED,
  FAILED
}
