package com.finrisk.radar.document.api;

import com.finrisk.radar.document.DocumentSourceType;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record DocumentCollectionRequest(
    @NotEmpty List<Long> assetIds,
    @NotEmpty List<DocumentSourceType> sourceTypes,
    @NotNull LocalDate fromDate,
    @NotNull LocalDate toDate) {}
