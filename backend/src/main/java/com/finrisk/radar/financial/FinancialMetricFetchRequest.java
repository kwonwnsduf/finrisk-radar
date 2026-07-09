package com.finrisk.radar.financial;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FinancialMetricFetchRequest(
		@Positive Long assetId,
		@Size(max = 20) String stockCode,
		@Min(2000) Integer year,
		@Min(1) @Max(4) Integer quarter
) {}
