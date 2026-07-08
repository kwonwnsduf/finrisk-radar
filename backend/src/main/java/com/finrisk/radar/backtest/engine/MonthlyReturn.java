package com.finrisk.radar.backtest.engine;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlyReturn(
		YearMonth month,
		BigDecimal returnRate
) {}
