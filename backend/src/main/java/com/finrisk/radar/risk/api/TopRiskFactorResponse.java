package com.finrisk.radar.risk.api;

import com.finrisk.radar.risk.*;

public record TopRiskFactorResponse(
    int rank,
    RiskCategory category,
    String signalType,
    RiskSeverity severity,
    int score,
    String summary,
    String evidence) {}
