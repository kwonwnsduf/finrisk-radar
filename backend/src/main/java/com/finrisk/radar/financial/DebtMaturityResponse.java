package com.finrisk.radar.financial;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DebtMaturityResponse(
		Long id,
		Long assetId,
		LocalDate maturityDate,
		BigDecimal amount,
		DebtType debtType,
		BigDecimal interestRate,
		String currency,
		boolean shortTerm,
		LocalDateTime createdAt
) {
	public static DebtMaturityResponse from(DebtMaturity maturity) {
		return new DebtMaturityResponse(maturity.getId(), maturity.getAsset().getId(), maturity.getMaturityDate(),
				maturity.getAmount(), maturity.getDebtType(), maturity.getInterestRate(), maturity.getCurrency(),
				maturity.isShortTerm(), maturity.getCreatedAt());
	}
}
