package com.finrisk.radar.watchlist;

import jakarta.validation.constraints.NotNull;

public record WatchlistCreateRequest(
		@NotNull(message = "Asset ID is required.")
		Long assetId
) {}
