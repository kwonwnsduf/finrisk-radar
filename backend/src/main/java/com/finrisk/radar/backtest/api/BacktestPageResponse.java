package com.finrisk.radar.backtest.api;

import java.util.List;

public record BacktestPageResponse(
    List<BacktestJobResponse> items, int page, int size, long totalElements, int totalPages) {}
