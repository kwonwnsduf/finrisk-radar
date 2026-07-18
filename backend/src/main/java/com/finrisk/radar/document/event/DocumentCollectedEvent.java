package com.finrisk.radar.document.event;

import java.time.Instant;
import java.util.*;

public record DocumentCollectedEvent(
    int eventVersion,
    UUID correlationId,
    Long documentId,
    List<Long> assetIds,
    int contentVersion,
    Instant occurredAt) {}
