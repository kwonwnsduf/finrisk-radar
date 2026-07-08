package com.finrisk.radar.backtest.strategy.custom;

import com.finrisk.radar.backtest.api.StrategyCondition;
import com.finrisk.radar.backtest.indicator.*;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class SellConditionEvaluator {
	public boolean matches(StrategyCondition condition, int index, List<MarketPriceResponse> prices,
			CustomPositionState position) {
		return switch (condition.type()) {
			case RSI_GREATER_THAN -> {
				BigDecimal rsi = value(new RsiCalculator().calculate(prices, period(condition.period(), 14)), index);
				yield rsi != null && rsi.compareTo(condition.value()) > 0;
			}
			case PRICE_BELOW_MA -> {
				BigDecimal ma = value(new MovingAverageCalculator().calculate(prices, condition.period()), index);
				yield ma != null && prices.get(index).close().compareTo(ma) < 0;
			}
			case MA_CROSS_DOWN -> crossMa(condition, index, prices);
			case BOLLINGER_UPPER_TOUCH -> {
				var band = new BollingerBandCalculator().calculate(prices, condition.period(), BigDecimal.valueOf(2)).get(index);
				yield band != null && prices.get(index).close().compareTo(band.upper()) >= 0;
			}
			case MACD_DEAD_CROSS -> macdCross(condition, index, prices);
			case STOP_LOSS -> profit(position.entryPrice(), prices.get(index).close()).compareTo(condition.value()) <= 0;
			case TAKE_PROFIT -> profit(position.entryPrice(), prices.get(index).close()).compareTo(condition.value()) >= 0;
			case TRAILING_STOP -> profit(position.highestPrice(), prices.get(index).close()).compareTo(condition.value().abs().negate()) <= 0;
			case MA_PREMIUM -> {
				BigDecimal deviation = deviation(condition, index, prices);
				yield deviation != null && deviation.compareTo(condition.value() == null ? BigDecimal.valueOf(5) : condition.value()) >= 0;
			}
			case DONCHIAN_LOW_BREAKDOWN -> {
				var channel = new DonchianChannelCalculator().calculate(prices, condition.period()).get(index);
				yield channel != null && prices.get(index).close().compareTo(channel.low()) < 0;
			}
			case MOMENTUM_NEGATIVE -> {
				BigDecimal momentum = value(new MomentumCalculator().calculate(prices, condition.period()), index);
				yield momentum != null && momentum.signum() < 0;
			}
			default -> false;
		};
	}

	private boolean crossMa(StrategyCondition condition, int index, List<MarketPriceResponse> prices) {
		if (index == 0) return false;
		var ma = new MovingAverageCalculator().calculate(prices, condition.period());
		if (ma.get(index) == null || ma.get(index - 1) == null) return false;
		return prices.get(index - 1).close().compareTo(ma.get(index - 1)) >= 0
				&& prices.get(index).close().compareTo(ma.get(index)) < 0;
	}

	private boolean macdCross(StrategyCondition condition, int index, List<MarketPriceResponse> prices) {
		if (index == 0) return false;
		var macd = new MacdCalculator().calculate(prices, period(condition.shortPeriod(), 12),
				period(condition.longPeriod(), 26), period(condition.signalPeriod(), 9));
		if (macd.get(index) == null || macd.get(index - 1) == null) return false;
		BigDecimal previous = macd.get(index - 1).macd().subtract(macd.get(index - 1).signal());
		BigDecimal current = macd.get(index).macd().subtract(macd.get(index).signal());
		return previous.signum() >= 0 && current.signum() < 0;
	}

	private BigDecimal deviation(StrategyCondition condition, int index, List<MarketPriceResponse> prices) {
		BigDecimal ma = value(new MovingAverageCalculator().calculate(prices, condition.period()), index);
		if (ma == null || ma.signum() == 0) return null;
		return prices.get(index).close().divide(ma, 10, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
				.multiply(BigDecimal.valueOf(100));
	}

	private BigDecimal profit(BigDecimal entry, BigDecimal current) {
		if (entry == null || entry.signum() == 0) return BigDecimal.ZERO;
		return current.divide(entry, 10, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100));
	}

	private BigDecimal value(List<BigDecimal> values, int index) {
		return values.get(index);
	}

	private int period(Integer period, int fallback) {
		return period == null ? fallback : period;
	}
}
