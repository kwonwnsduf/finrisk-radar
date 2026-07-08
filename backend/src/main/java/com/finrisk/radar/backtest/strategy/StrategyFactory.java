package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.strategy.custom.CustomStrategy;

import java.util.EnumMap;
import java.util.Map;

public class StrategyFactory {
	private final Map<StrategyType, BacktestStrategy> strategies = new EnumMap<>(StrategyType.class);

	public StrategyFactory() {
		register(new BuyAndHoldStrategy());
		register(new MovingAverageStrategy());
		register(new RsiStrategy());
		register(new BollingerBandStrategy());
		register(new MacdStrategy());
		register(new VolatilityBreakoutStrategy());
		register(new DcaStrategy());
		register(new MaDeviationStrategy());
		register(new DonchianChannelStrategy());
		register(new MomentumStrategy());
		register(new CustomStrategy());
	}

	public BacktestStrategy get(StrategyType type) {
		BacktestStrategy strategy = strategies.get(type);
		if (strategy == null) throw new IllegalArgumentException("Unsupported strategy.");
		return strategy;
	}

	private void register(BacktestStrategy strategy) {
		strategies.put(strategy.supports(), strategy);
	}
}
