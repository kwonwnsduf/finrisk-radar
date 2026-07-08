package com.finrisk.radar.backtest.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Trade(
		LocalDate date,
		TradeSide side,
		BigDecimal price,
		BigDecimal quantity,
		BigDecimal cashAfter,
		BigDecimal portfolioValueAfter,
		String reason
) {}
