package com.finrisk.radar.backtest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.backtest.BacktestStrategyConfig;
import com.finrisk.radar.backtest.BacktestJob;
import com.finrisk.radar.backtest.BacktestJobService;
import com.finrisk.radar.backtest.engine.BacktestCalculationResult;
import com.finrisk.radar.backtest.engine.BacktestContext;
import com.finrisk.radar.backtest.engine.BacktestEngine;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.marketprice.MarketPriceResponse;
import com.finrisk.radar.marketprice.MarketPriceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BacktestExecutionService {
	private final BacktestJobService jobs;
	private final MarketPriceService prices;
	private final BacktestEngine engine;
	private final ObjectMapper objectMapper;

	@Autowired
	public BacktestExecutionService(BacktestJobService jobs, MarketPriceService prices, BacktestEngine engine,
			ObjectMapper objectMapper) {
		this.jobs = jobs;
		this.prices = prices;
		this.engine = engine;
		this.objectMapper = objectMapper;
	}

	BacktestExecutionService(BacktestJobService jobs, MarketPriceService prices, BacktestEngine engine) {
		this(jobs, prices, engine, new ObjectMapper());
	}

	public void execute(UUID jobId) {
		if (!jobs.markRunning(jobId)) return;
		BacktestJob job = jobs.getInternal(jobId);
		try {
			List<MarketPriceResponse> priceData = prices.getPrices(job.getAssetId(), job.getStartDate(), job.getEndDate());
			BacktestCalculationResult calculation = shouldUseLegacyEngineCall(job)
					? engine.execute(job.getStrategyType(), priceData)
					: engine.execute(new BacktestContext(job.getStrategyType(), job.getInitialCash(), readConfig(job),
							java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO), priceData);
			jobs.completeWithResult(jobId, calculation);
		} catch (RuntimeException exception) {
			jobs.markFailed(jobId, safeMessage(exception));
			throw exception;
		}
	}

	private String safeMessage(RuntimeException exception) {
		if (exception instanceof BusinessException businessException)
			return businessException.getErrorCode().getMessage();
		return "Backtest failed while processing market prices.";
	}

	private BacktestStrategyConfig readConfig(BacktestJob job) {
		if (job.getStrategyConfig() == null || job.getStrategyConfig().isBlank()) return BacktestStrategyConfig.empty();
		try {
			return objectMapper.readValue(job.getStrategyConfig(), BacktestStrategyConfig.class);
		} catch (JsonProcessingException exception) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	private boolean shouldUseLegacyEngineCall(BacktestJob job) {
		return (job.getStrategyConfig() == null || job.getStrategyConfig().isBlank())
				&& job.getInitialCash().compareTo(BacktestContext.DEFAULT_INITIAL_CASH) == 0;
	}
}
