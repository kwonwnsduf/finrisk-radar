package com.finrisk.radar.backtest.engine;

import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class PerformanceCalculator {
	private static final int SCALE = 12;
	private static final int RESULT_SCALE = 6;

	public BacktestCalculationResult calculate(BacktestContext context, List<MarketPriceResponse> prices,
			BacktestSimulationResult simulation) {
		DailyPortfolioValue firstValue = simulation.dailyPortfolioValues().get(0);
		DailyPortfolioValue lastValue = simulation.dailyPortfolioValues().get(simulation.dailyPortfolioValues().size() - 1);
		BigDecimal totalReturn = percent(lastValue.portfolioValue(), context.initialCash());
		BigDecimal benchmark = percent(prices.get(prices.size() - 1).close(), prices.get(0).close());
		return new BacktestCalculationResult(
				prices.get(0).date(),
				prices.get(prices.size() - 1).date(),
				prices.get(0).close(),
				prices.get(prices.size() - 1).close(),
				totalReturn,
				cagr(context.initialCash(), lastValue.portfolioValue(), firstValue.date(), lastValue.date()),
				mdd(simulation.dailyPortfolioValues()),
				winRate(simulation.trades()),
				simulation.trades().size(),
				sharpe(simulation.dailyPortfolioValues()),
				benchmark,
				monthlyReturns(simulation.dailyPortfolioValues()),
				simulation.dailyPortfolioValues(),
				simulation.trades());
	}

	private BigDecimal percent(BigDecimal end, BigDecimal start) {
		if (start == null || start.signum() == 0) return BigDecimal.ZERO.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
		return end.divide(start, SCALE, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
				.multiply(BigDecimal.valueOf(100)).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
	}

	private BigDecimal cagr(BigDecimal initial, BigDecimal last, java.time.LocalDate start, java.time.LocalDate end) {
		long days = Math.max(1, ChronoUnit.DAYS.between(start, end));
		if (initial.signum() <= 0 || last.signum() <= 0) return BigDecimal.ZERO.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
		double years = days / 365.0;
		double value = (Math.pow(last.divide(initial, SCALE, RoundingMode.HALF_UP).doubleValue(), 1.0 / years) - 1.0) * 100.0;
		return BigDecimal.valueOf(value).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
	}

	private BigDecimal mdd(List<DailyPortfolioValue> values) {
		BigDecimal peak = values.get(0).portfolioValue();
		BigDecimal worst = BigDecimal.ZERO;
		for (DailyPortfolioValue value : values) {
			if (value.portfolioValue().compareTo(peak) > 0) peak = value.portfolioValue();
			BigDecimal drawdown = percent(value.portfolioValue(), peak);
			if (drawdown.compareTo(worst) < 0) worst = drawdown;
		}
		return worst.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
	}

	private BigDecimal winRate(List<Trade> trades) {
		BigDecimal lastBuy = null;
		int sells = 0;
		int wins = 0;
		for (Trade trade : trades) {
			if (trade.side() == TradeSide.BUY) lastBuy = trade.price();
			if (trade.side() == TradeSide.SELL && lastBuy != null) {
				sells++;
				if (trade.price().compareTo(lastBuy) > 0) wins++;
			}
		}
		if (sells == 0) return BigDecimal.ZERO.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
		return BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(sells), SCALE, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100)).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
	}

	private BigDecimal sharpe(List<DailyPortfolioValue> values) {
		if (values.size() < 2) return BigDecimal.ZERO.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
		List<Double> returns = new ArrayList<>();
		for (int i = 1; i < values.size(); i++) {
			BigDecimal previous = values.get(i - 1).portfolioValue();
			if (previous.signum() > 0) {
				returns.add(values.get(i).portfolioValue().divide(previous, SCALE, RoundingMode.HALF_UP)
						.subtract(BigDecimal.ONE).doubleValue());
			}
		}
		if (returns.isEmpty()) return BigDecimal.ZERO.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
		double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
		double variance = returns.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0);
		double sd = Math.sqrt(variance);
		if (sd == 0) return BigDecimal.ZERO.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
		return BigDecimal.valueOf(mean / sd * Math.sqrt(252)).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
	}

	private List<MonthlyReturn> monthlyReturns(List<DailyPortfolioValue> values) {
		List<MonthlyReturn> result = new ArrayList<>();
		YearMonth current = YearMonth.from(values.get(0).date());
		BigDecimal start = values.get(0).portfolioValue();
		DailyPortfolioValue previous = values.get(0);
		for (DailyPortfolioValue value : values) {
			YearMonth month = YearMonth.from(value.date());
			if (!month.equals(current)) {
				result.add(new MonthlyReturn(current, percent(previous.portfolioValue(), start)));
				current = month;
				start = previous.portfolioValue();
			}
			previous = value;
		}
		result.add(new MonthlyReturn(current, percent(previous.portfolioValue(), start)));
		return result;
	}
}
