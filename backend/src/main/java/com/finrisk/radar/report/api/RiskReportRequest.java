package com.finrisk.radar.report.api;

import jakarta.validation.constraints.*;

public record RiskReportRequest(
    @Positive Long assetId, @NotBlank @Size(min = 10, max = 500) String question) {}
