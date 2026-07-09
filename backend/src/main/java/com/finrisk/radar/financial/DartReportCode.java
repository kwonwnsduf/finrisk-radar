package com.finrisk.radar.financial;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;

public final class DartReportCode {
	private DartReportCode() {}

	public static String fromQuarter(Integer quarter) {
		return switch (quarter) {
			case 1 -> "11013";
			case 2 -> "11012";
			case 3 -> "11014";
			case 4 -> "11011";
			default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
		};
	}
}
