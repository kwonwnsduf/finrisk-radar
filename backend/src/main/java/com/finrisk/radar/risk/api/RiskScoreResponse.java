package com.finrisk.radar.risk.api;

import com.finrisk.radar.risk.*;
import java.time.*;
import java.util.*;

public record RiskScoreResponse(
    Long id,
    Long assetId,
    int totalScore,
    RiskGrade riskGrade,
    DefaultStatus defaultStatus,
    Integer financialScore,
    Integer liquidityScore,
    Integer marketScore,
    Integer creditEventScore,
    Integer groupContagionScore,
    String categoryStatuses,
    RiskDataQuality dataQuality,
    RiskConfidence confidence,
    int requiredRuleSuccessRate,
    String missingCategories,
    Map<String, Integer> usedDataCounts,
    LocalDate dataAsOfDate,
    String ruleVersion,
    LocalDateTime calculatedAt,
    List<TopRiskFactorResponse> topRiskFactors) {
  public static RiskScoreResponse from(RiskScore s, List<TopRiskFactorResponse> factors) {
    return new RiskScoreResponse(
        s.getId(),
        s.getAssetId(),
        s.getTotalScore(),
        s.getRiskGrade(),
        s.getDefaultStatus(),
        s.getFinancialScore(),
        s.getLiquidityScore(),
        s.getMarketScore(),
        s.getCreditEventScore(),
        s.getGroupContagionScore(),
        s.getCategoryStatuses(),
        s.getDataQuality(),
        s.getConfidence(),
        s.getRequiredRuleSuccessRate(),
        s.getMissingCategories(),
        Map.of(
            "financialMetrics",
            s.getFinancialCount(),
            "debtMaturities",
            s.getDebtCount(),
            "marketPrices",
            s.getMarketCount(),
            "creditEvents",
            s.getEventCount(),
            "relationships",
            s.getRelationshipCount()),
        s.getDataAsOfDate(),
        s.getRuleVersion(),
        s.getCalculatedAt(),
        factors);
  }
}
