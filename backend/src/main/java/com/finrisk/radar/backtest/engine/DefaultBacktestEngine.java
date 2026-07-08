package com.finrisk.radar.backtest.engine;

import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.strategy.StrategyFactory;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.marketprice.MarketPriceResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DefaultBacktestEngine implements BacktestEngine {
	private final StrategyFactory strategies = new StrategyFactory();
	private final PerformanceCalculator performance = new PerformanceCalculator();

	@Override
	public BacktestCalculationResult execute(StrategyType strategyType, List<MarketPriceResponse> prices) {
		return execute(BacktestContext.defaults(strategyType), prices);
	}

	@Override
	public BacktestCalculationResult execute(BacktestContext context, List<MarketPriceResponse> prices) {
		List<MarketPriceResponse> ordered = validateAndSort(prices);
		BacktestSimulationResult simulation = strategies.get(context.strategyType()).simulate(context, ordered);
		return performance.calculate(context, ordered, simulation);
	}

	private List<MarketPriceResponse> validateAndSort(List<MarketPriceResponse> prices) {
		if (prices == null || prices.isEmpty()) throw new BusinessException(ErrorCode.BACKTEST_PRICE_DATA_NOT_FOUND);
		List<MarketPriceResponse> ordered = prices.stream()
				.sorted(Comparator.comparing(MarketPriceResponse::date))
				.toList();
		for (MarketPriceResponse price : ordered) {
			if (price.open() == null || price.high() == null || price.low() == null || price.close() == null
					|| price.volume() == null || price.close().signum() <= 0 || price.open().signum() <= 0
					|| price.high().signum() <= 0 || price.low().signum() <= 0) {
				throw new BusinessException(ErrorCode.BACKTEST_PRICE_DATA_INVALID);
			}
		}
		return ordered;
	}
}
