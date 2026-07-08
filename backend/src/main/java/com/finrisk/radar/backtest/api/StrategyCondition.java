package com.finrisk.radar.backtest.api;

import com.finrisk.radar.backtest.CustomConditionType;

import java.math.BigDecimal;

public record StrategyCondition(
		CustomConditionType type,
		Integer period,
		Integer shortPeriod,
		Integer longPeriod,
		Integer signalPeriod,
		BigDecimal value
) {}
