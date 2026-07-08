package com.finrisk.radar.backtest.strategy.custom;

import java.math.BigDecimal;

public record CustomPositionState(
		BigDecimal entryPrice,
		BigDecimal highestPrice
) {}
