package com.finrisk.radar.risk.api;

import com.finrisk.radar.risk.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreditEventCreateRequest(
    @NotNull CreditEventType eventType,
    @NotNull LocalDate eventDate,
    @NotNull RiskSeverity severity,
    @NotBlank String sourceType,
    String sourceName,
    String sourceDocumentId,
    @Size(max = 2000) String description,
    String incidentKey,
    @NotBlank String externalEventKey) {}
