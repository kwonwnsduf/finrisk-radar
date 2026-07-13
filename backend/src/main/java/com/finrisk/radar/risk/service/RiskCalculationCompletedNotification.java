package com.finrisk.radar.risk.service;

import com.finrisk.radar.risk.*;
import java.util.*;

public record RiskCalculationCompletedNotification(
    UUID jobId, Long assetId, RiskScore score, List<RiskSignal> signals) {}
