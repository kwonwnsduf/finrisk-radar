package com.finrisk.radar.fsd.event;

import com.finrisk.radar.fsd.*;
import java.time.Instant;

public record FsdReviewRequiredEvent(
    Long fsdEventId,
    Long paymentOrderId,
    FsdDecision decision,
    FsdSeverity severity,
    Instant detectedAt) {}
