package com.finrisk.radar.financial;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FinancialMetricResponse(
		Long id,
		Long assetId,
		Integer year,
		Integer quarter,
		BigDecimal revenue,
		BigDecimal operatingIncome,
		BigDecimal netIncome,
		BigDecimal totalDebt,
		BigDecimal totalEquity,
		BigDecimal cash,
		BigDecimal operatingCashFlow,
		BigDecimal interestExpense,
		BigDecimal debtRatio,
		BigDecimal interestCoverageRatio,
		LocalDateTime createdAt
) {
	public static FinancialMetricResponse from(FinancialMetric metric) {
		return new FinancialMetricResponse(metric.getId(), metric.getAsset().getId(), metric.getYear(), metric.getQuarter(),
				metric.getRevenue(), metric.getOperatingIncome(), metric.getNetIncome(), metric.getTotalDebt(),
				metric.getTotalEquity(), metric.getCash(), metric.getOperatingCashFlow(), metric.getInterestExpense(),
				metric.getDebtRatio(), metric.getInterestCoverageRatio(), metric.getCreatedAt());
	}
}
