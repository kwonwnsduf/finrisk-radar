package com.finrisk.radar.financial;

public record RawDartFinancialStatement(
		String corpCode,
		Integer year,
		Integer quarter,
		DartStatementDivision division,
		boolean fallbackUsed,
		String payload
) {}
