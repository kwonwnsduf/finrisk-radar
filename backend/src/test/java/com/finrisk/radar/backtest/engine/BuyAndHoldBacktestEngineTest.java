package com.finrisk.radar.backtest.engine;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.marketprice.MarketPriceResponse;
import com.finrisk.radar.marketprice.MarketPriceSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuyAndHoldBacktestEngineTest {
	private final BuyAndHoldBacktestEngine engine = new BuyAndHoldBacktestEngine();

	@Test
	void calculatesPositiveReturn() {
		BacktestCalculationResult result = engine.execute(StrategyType.BUY_AND_HOLD,
				List.of(price("2024-01-02", "100"), price("2024-12-30", "110")));

		assertThat(result.totalReturnRate()).isEqualByComparingTo("10.000000");
		assertThat(result.firstPriceDate()).isEqualTo(LocalDate.parse("2024-01-02"));
		assertThat(result.lastPriceDate()).isEqualTo(LocalDate.parse("2024-12-30"));
	}

	@Test
	void calculatesNegativeReturn() {
		BacktestCalculationResult result = engine.execute(StrategyType.BUY_AND_HOLD,
				List.of(price("2024-01-02", "100"), price("2024-12-30", "90")));
		assertThat(result.totalReturnRate()).isEqualByComparingTo("-10.000000");
	}

	@Test
	void onePriceProducesZeroReturn() {
		BacktestCalculationResult result = engine.execute(StrategyType.BUY_AND_HOLD,
				List.of(price("2024-01-02", "100")));
		assertThat(result.totalReturnRate()).isEqualByComparingTo("0.000000");
	}

	@Test
	void rejectsMissingPrices() {
		assertThatThrownBy(() -> engine.execute(StrategyType.BUY_AND_HOLD, List.of()))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.BACKTEST_PRICE_DATA_NOT_FOUND);
	}

	@Test
	void rejectsAZeroInitialPrice() {
		assertThatThrownBy(() -> engine.execute(StrategyType.BUY_AND_HOLD,
				List.of(price("2024-01-02", "0"))))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.BACKTEST_PRICE_DATA_INVALID);
	}

	private MarketPriceResponse price(String date, String close) {
		BigDecimal value = new BigDecimal(close);
		return new MarketPriceResponse(1L, LocalDate.parse(date), value, value, value, value, 100L,
				MarketPriceSource.YAHOO);
	}
}
