package com.finrisk.radar.risk.api;

import com.finrisk.radar.risk.AssetRelationshipType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetRelationshipCreateRequest(
    @NotNull Long fromAssetId,
    @NotNull Long toAssetId,
    @NotNull AssetRelationshipType relationshipType,
    @PositiveOrZero BigDecimal exposureAmount,
    String currency,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String description) {}
