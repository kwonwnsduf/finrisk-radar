package com.finrisk.radar.risk.event;

import com.finrisk.radar.risk.RiskSeverity;
import java.util.UUID;

public record RiskSignalDetectedEvent(
    UUID jobId,
    Long assetId,
    Long riskScoreId,
    Long signalId,
    String signalType,
    RiskSeverity severity) {}
