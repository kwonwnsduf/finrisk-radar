package com.finrisk.radar.risk.engine;

import com.finrisk.radar.risk.*;
import java.util.*;

public record RiskCalculationOutcome(
    int totalScore,
    RiskGrade riskGrade,
    DefaultStatus defaultStatus,
    Map<RiskCategory, Integer> scores,
    Map<RiskCategory, CategoryCalculationStatus> statuses,
    RiskDataQuality dataQuality,
    RiskConfidence confidence,
    int requiredRuleSuccessRate,
    List<String> missingCategories,
    List<RiskRuleResult> results,
    RiskJobExecutionSummary summary) {}
