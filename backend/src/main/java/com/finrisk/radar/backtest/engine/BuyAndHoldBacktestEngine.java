package com.finrisk.radar.backtest.engine;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.marketprice.MarketPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class BuyAndHoldBacktestEngine implements BacktestEngine {
	private static final int CALCULATION_SCALE = 12;
	private static final int RESULT_SCALE = 6;

	@Override
	public BacktestCalculationResult execute(StrategyType strategyType, List<MarketPriceResponse> prices) {
		if (strategyType != StrategyType.BUY_AND_HOLD) throw new IllegalArgumentException("Unsupported strategy.");
		if (prices == null || prices.isEmpty()) throw new BusinessException(ErrorCode.BACKTEST_PRICE_DATA_NOT_FOUND);

		MarketPriceResponse first = prices.get(0);
		MarketPriceResponse last = prices.get(prices.size() - 1);
		if (first.close() == null || last.close() == null || first.close().signum() <= 0 || last.close().signum() < 0)
			throw new BusinessException(ErrorCode.BACKTEST_PRICE_DATA_INVALID);

		BigDecimal rate = last.close()
				.divide(first.close(), CALCULATION_SCALE, RoundingMode.HALF_UP)
				.subtract(BigDecimal.ONE)
				.multiply(BigDecimal.valueOf(100))
				.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
		return new BacktestCalculationResult(first.date(), last.date(), first.close(), last.close(), rate);
	}
}
