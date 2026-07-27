package com.finrisk.radar.payment.outbox;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record PaymentDomainEvent(
    UUID eventId,
    String eventType,
    LocalDateTime occurredAt,
    Map<String, Object> payload,
    int eventVersion) {}
