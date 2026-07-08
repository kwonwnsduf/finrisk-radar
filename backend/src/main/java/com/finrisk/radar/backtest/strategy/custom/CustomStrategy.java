package com.finrisk.radar.backtest.strategy.custom;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.api.StrategyCondition;
import com.finrisk.radar.backtest.engine.*;
import com.finrisk.radar.backtest.strategy.BacktestStrategy;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class CustomStrategy implements BacktestStrategy {
	private static final int SCALE = 12;
	private final BuyConditionEvaluator buyEvaluator = new BuyConditionEvaluator();
	private final SellConditionEvaluator sellEvaluator = new SellConditionEvaluator();

	@Override
	public StrategyType supports() {
		return StrategyType.CUSTOM;
	}

	@Override
	public BacktestSimulationResult simulate(BacktestContext context, List<MarketPriceResponse> prices) {
		BigDecimal cash = context.initialCash();
		BigDecimal quantity = BigDecimal.ZERO;
		BigDecimal entryPrice = null;
		BigDecimal highestPrice = null;
		List<Trade> trades = new ArrayList<>();
		List<DailyPortfolioValue> values = new ArrayList<>();

		for (int i = 0; i < prices.size(); i++) {
			MarketPriceResponse price = prices.get(i);
			if (quantity.signum() > 0) {
				if (highestPrice == null || price.close().compareTo(highestPrice) > 0) highestPrice = price.close();
				CustomPositionState position = new CustomPositionState(entryPrice, highestPrice);
				if (matchesSell(context.strategyConfig().sellConditions(), i, prices, position)) {
					BigDecimal soldQuantity = quantity;
					cash = quantity.multiply(price.close()).setScale(6, RoundingMode.HALF_UP);
					quantity = BigDecimal.ZERO;
					trades.add(trade(price, TradeSide.SELL, soldQuantity, cash, "CUSTOM_SELL"));
					entryPrice = null;
					highestPrice = null;
				}
			}
			if (quantity.signum() == 0 && cash.signum() > 0
					&& matchesBuy(context.strategyConfig().buyConditions(), i, prices)) {
				entryPrice = price.close();
				highestPrice = price.close();
				quantity = cash.divide(price.close(), SCALE, RoundingMode.HALF_UP);
				cash = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
				trades.add(trade(price, TradeSide.BUY, quantity, cash, "CUSTOM_BUY"));
			}
			values.add(value(price, cash, quantity));
		}
		return new BacktestSimulationResult(values, trades);
	}

	private boolean matchesBuy(List<StrategyCondition> conditions, int index, List<MarketPriceResponse> prices) {
		return conditions.stream().allMatch(condition -> buyEvaluator.matches(condition, index, prices));
	}

	private boolean matchesSell(List<StrategyCondition> conditions, int index, List<MarketPriceResponse> prices,
			CustomPositionState position) {
		return conditions.stream().anyMatch(condition -> sellEvaluator.matches(condition, index, prices, position));
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
