package com.finrisk.radar.backtest.api;

import com.finrisk.radar.backtest.BacktestResult;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BacktestResultResponse(
		LocalDate firstPriceDate,
		LocalDate lastPriceDate,
		BigDecimal initialClose,
		BigDecimal finalClose,
		BigDecimal totalReturnRate
) {
	public static BacktestResultResponse from(BacktestResult result) {
		return new BacktestResultResponse(result.getFirstPriceDate(), result.getLastPriceDate(),
				result.getInitialClose(), result.getFinalClose(), result.getTotalReturnRate());
	}
}
