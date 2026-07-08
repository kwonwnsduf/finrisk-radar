package com.finrisk.radar.backtest.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.backtest.BacktestResult;
import com.finrisk.radar.backtest.engine.DailyPortfolioValue;
import com.finrisk.radar.backtest.engine.MonthlyReturn;
import com.finrisk.radar.backtest.engine.Trade;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BacktestResultResponse(
		LocalDate firstPriceDate,
		LocalDate lastPriceDate,
		BigDecimal initialClose,
		BigDecimal finalClose,
		BigDecimal totalReturnRate,
		BigDecimal cagr,
		BigDecimal mdd,
		BigDecimal winRate,
		Integer tradeCount,
		BigDecimal sharpeRatio,
		BigDecimal benchmarkReturnRate,
		List<MonthlyReturn> monthlyReturns,
		List<DailyPortfolioValue> dailyPortfolioValues,
		List<Trade> trades
) {
	public static BacktestResultResponse from(BacktestResult result, ObjectMapper objectMapper) {
		return new BacktestResultResponse(result.getFirstPriceDate(), result.getLastPriceDate(),
				result.getInitialClose(), result.getFinalClose(), result.getTotalReturnRate(),
				result.getCagr(), result.getMdd(), result.getWinRate(), result.getTradeCount(),
				result.getSharpeRatio(), result.getBenchmarkReturnRate(),
				read(objectMapper, result.getMonthlyReturns(), new TypeReference<List<MonthlyReturn>>() {}),
				read(objectMapper, result.getDailyPortfolioValues(), new TypeReference<List<DailyPortfolioValue>>() {}),
				read(objectMapper, result.getTrades(), new TypeReference<List<Trade>>() {}));
	}

	private static <T> T read(ObjectMapper objectMapper, String json, TypeReference<T> type) {
		try {
			return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, type);
		} catch (JsonProcessingException exception) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}
}
