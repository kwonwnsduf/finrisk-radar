package com.finrisk.radar.backtest.engine;

import com.finrisk.radar.backtest.BacktestStrategyConfig;
import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.api.StrategyCondition;
import com.finrisk.radar.marketprice.MarketPriceResponse;
import com.finrisk.radar.marketprice.MarketPriceSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.finrisk.radar.backtest.CustomConditionType.*;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultBacktestEngineTest {
	private final DefaultBacktestEngine engine = new DefaultBacktestEngine();

	@Test
	void buyAndHoldStillCalculatesTheOriginalReturn() {
		BacktestCalculationResult result = engine.execute(StrategyType.BUY_AND_HOLD,
				List.of(price(0, "100"), price(1, "110")));

		assertThat(result.totalReturnRate()).isEqualByComparingTo("10.000000");
		assertThat(result.trades()).hasSize(1);
		assertThat(result.dailyPortfolioValues()).hasSize(2);
	}

	@Test
	void customStrategyUsesBuyAndSellConditions() {
		BacktestContext context = new BacktestContext(StrategyType.CUSTOM, new BigDecimal("10000000"),
				new BacktestStrategyConfig(
						List.of(new StrategyCondition(PRICE_ABOVE_MA, 2, null, null, null, null)),
						List.of(new StrategyCondition(TAKE_PROFIT, null, null, null, null, BigDecimal.ONE))),
				BigDecimal.ZERO, BigDecimal.ZERO);

		BacktestCalculationResult result = engine.execute(context, risingPrices());

		assertThat(result.trades()).extracting(Trade::side).contains(TradeSide.BUY, TradeSide.SELL);
		assertThat(result.tradeCount()).isGreaterThanOrEqualTo(2);
	}

	private List<MarketPriceResponse> risingPrices() {
		List<MarketPriceResponse> prices = new ArrayList<>();
		for (int i = 0; i < 8; i++) prices.add(price(i, String.valueOf(100 + i * 2)));
		return prices;
	}

	private MarketPriceResponse price(int dayOffset, String close) {
		BigDecimal value = new BigDecimal(close);
		return new MarketPriceResponse(1L, LocalDate.parse("2024-01-01").plusDays(dayOffset),
				value, value, value, value, 1000L, MarketPriceSource.YAHOO);
	}
}
