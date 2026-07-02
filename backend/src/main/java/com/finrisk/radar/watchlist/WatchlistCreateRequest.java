package com.finrisk.radar.watchlist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WatchlistCreateRequest(
		@NotBlank(message = "Asset code is required.")
		@Size(max = 100, message = "Asset code must be 100 characters or fewer.")
		String assetCode
) {}
