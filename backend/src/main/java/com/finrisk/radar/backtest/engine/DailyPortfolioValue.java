package com.finrisk.radar.backtest.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPortfolioValue(
		LocalDate date,
		BigDecimal cash,
		BigDecimal positionQuantity,
		BigDecimal closePrice,
		BigDecimal portfolioValue
) {}
