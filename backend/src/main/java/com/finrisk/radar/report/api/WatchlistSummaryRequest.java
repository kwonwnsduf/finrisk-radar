package com.finrisk.radar.report.api;

import jakarta.validation.constraints.Size;

public record WatchlistSummaryRequest(@Size(max = 500) String question) {}
