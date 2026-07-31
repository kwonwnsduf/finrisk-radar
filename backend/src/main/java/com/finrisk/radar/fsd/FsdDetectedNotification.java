package com.finrisk.radar.fsd;

import java.time.Instant;

public record FsdDetectedNotification(
    Long fsdEventId,
    Long paymentOrderId,
    FsdDecision decision,
    FsdSeverity severity,
    Instant detectedAt) {}
