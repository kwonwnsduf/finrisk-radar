package com.finrisk.radar.backtest.strategy.custom;

import com.finrisk.radar.backtest.api.StrategyCondition;
import com.finrisk.radar.backtest.indicator.*;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class BuyConditionEvaluator {
	public boolean matches(StrategyCondition condition, int index, List<MarketPriceResponse> prices) {
		return switch (condition.type()) {
			case RSI_LESS_THAN -> {
				BigDecimal rsi = value(new RsiCalculator().calculate(prices, period(condition, 14)), index);
				yield rsi != null && rsi.compareTo(condition.value()) < 0;
			}
			case PRICE_ABOVE_MA -> {
				BigDecimal ma = value(new MovingAverageCalculator().calculate(prices, condition.period()), index);
				yield ma != null && prices.get(index).close().compareTo(ma) > 0;
			}
			case MA_CROSS_UP -> crossMa(condition, index, prices, true);
			case BOLLINGER_LOWER_TOUCH -> {
				var band = new BollingerBandCalculator().calculate(prices, condition.period(), BigDecimal.valueOf(2)).get(index);
				yield band != null && prices.get(index).close().compareTo(band.lower()) <= 0;
			}
			case MACD_GOLDEN_CROSS -> macdCross(condition, index, prices, true);
			case VOLUME_SPIKE -> {
				BigDecimal ratio = value(new VolumeSpikeCalculator().ratios(prices, condition.period()), index);
				yield ratio != null && ratio.compareTo(condition.value() == null ? BigDecimal.valueOf(2) : condition.value()) >= 0;
			}
			case BREAKOUT -> {
				var target = new VolatilityCalculator().breakoutTargets(prices, BigDecimal.valueOf(0.5)).get(index);
				yield target != null && prices.get(index).high().compareTo(target) >= 0;
			}
			case MA_DISCOUNT -> {
				BigDecimal deviation = deviation(condition, index, prices);
				yield deviation != null && deviation.compareTo(condition.value() == null ? BigDecimal.valueOf(-5) : condition.value()) <= 0;
			}
			case DONCHIAN_HIGH_BREAKOUT -> {
				var channel = new DonchianChannelCalculator().calculate(prices, condition.period()).get(index);
				yield channel != null && prices.get(index).close().compareTo(channel.high()) > 0;
			}
			case MOMENTUM_POSITIVE -> {
				BigDecimal momentum = value(new MomentumCalculator().calculate(prices, condition.period()), index);
				yield momentum != null && momentum.signum() > 0;
			}
			default -> false;
		};
	}

	private boolean crossMa(StrategyCondition condition, int index, List<MarketPriceResponse> prices, boolean up) {
		if (index == 0) return false;
		var ma = new MovingAverageCalculator().calculate(prices, condition.period());
		if (ma.get(index) == null || ma.get(index - 1) == null) return false;
		int previous = prices.get(index - 1).close().compareTo(ma.get(index - 1));
		int current = prices.get(index).close().compareTo(ma.get(index));
		return up ? previous <= 0 && current > 0 : previous >= 0 && current < 0;
	}

	private boolean macdCross(StrategyCondition condition, int index, List<MarketPriceResponse> prices, boolean up) {
		if (index == 0) return false;
		var macd = new MacdCalculator().calculate(prices, period(condition.shortPeriod(), 12),
				period(condition.longPeriod(), 26), period(condition.signalPeriod(), 9));
		if (macd.get(index) == null || macd.get(index - 1) == null) return false;
		BigDecimal previous = macd.get(index - 1).macd().subtract(macd.get(index - 1).signal());
		BigDecimal current = macd.get(index).macd().subtract(macd.get(index).signal());
		return up ? previous.signum() <= 0 && current.signum() > 0 : previous.signum() >= 0 && current.signum() < 0;
	}

	private BigDecimal deviation(StrategyCondition condition, int index, List<MarketPriceResponse> prices) {
		BigDecimal ma = value(new MovingAverageCalculator().calculate(prices, condition.period()), index);
		if (ma == null || ma.signum() == 0) return null;
		return prices.get(index).close().divide(ma, 10, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
				.multiply(BigDecimal.valueOf(100));
	}

	private BigDecimal value(List<BigDecimal> values, int index) {
		return values.get(index);
	}

	private int period(StrategyCondition condition, int fallback) {
		return period(condition.period(), fallback);
	}

	private int period(Integer period, int fallback) {
		return period == null ? fallback : period;
	}
}
