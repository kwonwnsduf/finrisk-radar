package com.finrisk.radar.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssetCreateRequest(
		@NotBlank(message = "Asset name is required.")
		@Size(max = 200, message = "Asset name must be 200 characters or fewer.")
		String name,
		@NotBlank(message = "Ticker is required.")
		@Size(max = 100, message = "Ticker must be 100 characters or fewer.")
		String ticker,
		@Size(max = 50, message = "Market must be 50 characters or fewer.")
		String market,
		@Size(max = 100, message = "Sector must be 100 characters or fewer.")
		String sector,
		@Size(max = 10, message = "Country must be 10 characters or fewer.")
		String country,
		@Size(max = 10, message = "Currency must be 10 characters or fewer.")
		String currency,
		@NotNull(message = "Asset type is required.")
		AssetType assetType
) {}
