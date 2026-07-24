package com.finrisk.radar.backtest.api;

import com.finrisk.radar.asset.AssetResponse;
import com.finrisk.radar.backtest.BacktestStatus;
import java.util.*;

public record NaturalLanguageBacktestResponse(
    String outcome,
    UUID jobId,
    BacktestStatus status,
    BacktestCreateRequest parsedRequest,
    List<String> missingFields,
    List<AssetResponse> assetCandidates) {
  public static NaturalLanguageBacktestResponse clarification(
      BacktestCreateRequest draft, List<String> missing, List<AssetResponse> candidates) {
    return new NaturalLanguageBacktestResponse(
        "NEEDS_CLARIFICATION", null, null, draft, missing, candidates);
  }

  public static NaturalLanguageBacktestResponse accepted(
      BacktestCreateResponse response, BacktestCreateRequest request) {
    return new NaturalLanguageBacktestResponse(
        "ACCEPTED", response.jobId(), response.status(), request, List.of(), List.of());
  }
}
