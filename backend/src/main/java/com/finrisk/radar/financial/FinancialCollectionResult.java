package com.finrisk.radar.financial;

import java.util.UUID;

public record FinancialCollectionResult(
		UUID jobId,
		Long assetId,
		String stockCode,
		String corpCode,
		Integer year,
		Integer quarter,
		String statementDivision,
		boolean fallbackUsed,
		int recordCount,
		String rawS3Path
) {}
