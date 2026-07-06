package com.finrisk.radar.backtest.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BacktestCalculationResult(
		LocalDate firstPriceDate,
		LocalDate lastPriceDate,
		BigDecimal initialClose,
		BigDecimal finalClose,
		BigDecimal totalReturnRate
) {}
