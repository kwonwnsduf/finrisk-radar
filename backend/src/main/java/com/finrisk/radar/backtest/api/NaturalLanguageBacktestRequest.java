package com.finrisk.radar.backtest.api;

import jakarta.validation.constraints.*;

public record NaturalLanguageBacktestRequest(
    @NotBlank @Size(max = 500) String question, @Positive Long assetId) {}
