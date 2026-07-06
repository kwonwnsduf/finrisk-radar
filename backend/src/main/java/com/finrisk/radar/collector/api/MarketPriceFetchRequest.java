package com.finrisk.radar.collector.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MarketPriceFetchRequest(
		@NotNull @Positive Long assetId,
		@NotBlank @Size(max = 100) String ticker,
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate
) {}
