package com.finrisk.radar.backtest.engine;

import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class TradingSimulator {
	private static final int SCALE = 12;

	@FunctionalInterface
	public interface Signal {
		boolean test(int index, List<MarketPriceResponse> prices);
	}

	public BacktestSimulationResult run(BacktestContext context, List<MarketPriceResponse> prices,
			Signal buySignal, Signal sellSignal) {
		BigDecimal cash = context.initialCash();
		BigDecimal quantity = BigDecimal.ZERO;
		List<Trade> trades = new ArrayList<>();
		List<DailyPortfolioValue> values = new ArrayList<>();
		for (int i = 0; i < prices.size(); i++) {
			MarketPriceResponse price = prices.get(i);
			if (quantity.signum() > 0 && sellSignal.test(i, prices)) {
				BigDecimal soldQuantity = quantity;
				cash = quantity.multiply(price.close()).setScale(6, RoundingMode.HALF_UP);
				quantity = BigDecimal.ZERO;
				trades.add(trade(price, TradeSide.SELL, soldQuantity, cash, "SELL_SIGNAL"));
			} else if (quantity.signum() == 0 && cash.signum() > 0 && buySignal.test(i, prices)) {
				quantity = cash.divide(price.close(), SCALE, RoundingMode.HALF_UP);
				cash = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
				trades.add(trade(price, TradeSide.BUY, quantity, cash, "BUY_SIGNAL"));
			}
			values.add(value(price, cash, quantity));
		}
		return new BacktestSimulationResult(values, trades);
	}

	public BacktestSimulationResult dca(BacktestContext context, List<MarketPriceResponse> prices, int installments) {
		BigDecimal cash = context.initialCash();
		BigDecimal quantity = BigDecimal.ZERO;
		BigDecimal budget = context.initialCash().divide(BigDecimal.valueOf(Math.max(1, installments)), SCALE, RoundingMode.HALF_UP);
		List<Trade> trades = new ArrayList<>();
		List<DailyPortfolioValue> values = new ArrayList<>();
		int lastMonth = -1;
		for (MarketPriceResponse price : prices) {
			if (price.date().getMonthValue() != lastMonth && cash.signum() > 0) {
				BigDecimal amount = cash.min(budget);
				BigDecimal bought = amount.divide(price.close(), SCALE, RoundingMode.HALF_UP);
				quantity = quantity.add(bought);
				cash = cash.subtract(amount).setScale(6, RoundingMode.HALF_UP);
				trades.add(trade(price, TradeSide.BUY, quantity, cash, "DCA_MONTHLY_BUY"));
				lastMonth = price.date().getMonthValue();
			}
			values.add(value(price, cash, quantity));
		}
		return new BacktestSimulationResult(values, trades);
	}

	private Trade trade(MarketPriceResponse price, TradeSide side, BigDecimal quantity,
			BigDecimal cash, String reason) {
		BigDecimal portfolioValue = cash.add(quantity.multiply(price.close())).setScale(6, RoundingMode.HALF_UP);
		return new Trade(price.date(), side, price.close(), quantity, cash, portfolioValue, reason);
	}

	private DailyPortfolioValue value(MarketPriceResponse price, BigDecimal cash, BigDecimal quantity) {
		return new DailyPortfolioValue(price.date(), cash, quantity, price.close(),
				cash.add(quantity.multiply(price.close())).setScale(6, RoundingMode.HALF_UP));
	}
}
