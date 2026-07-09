package com.finrisk.radar.financial;

import java.math.BigDecimal;

public record FinancialMetricValues(
		BigDecimal revenue,
		BigDecimal operatingIncome,
		BigDecimal netIncome,
		BigDecimal totalDebt,
		BigDecimal totalEquity,
		BigDecimal cash,
		BigDecimal operatingCashFlow,
		BigDecimal interestExpense,
		BigDecimal debtRatio,
		BigDecimal interestCoverageRatio
) {}
