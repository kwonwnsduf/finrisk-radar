package com.finrisk.radar.backtest.service;

import com.finrisk.radar.backtest.BacktestJob;
import com.finrisk.radar.backtest.BacktestJobService;
import com.finrisk.radar.backtest.engine.BacktestCalculationResult;
import com.finrisk.radar.backtest.engine.BacktestEngine;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.marketprice.MarketPriceResponse;
import com.finrisk.radar.marketprice.MarketPriceService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BacktestExecutionService {
	private final BacktestJobService jobs;
	private final MarketPriceService prices;
	private final BacktestEngine engine;

	public BacktestExecutionService(BacktestJobService jobs, MarketPriceService prices, BacktestEngine engine) {
		this.jobs = jobs;
		this.prices = prices;
		this.engine = engine;
	}

	public void execute(UUID jobId) {
		if (!jobs.markRunning(jobId)) return;
		BacktestJob job = jobs.getInternal(jobId);
		try {
			List<MarketPriceResponse> priceData = prices.getPrices(job.getAssetId(), job.getStartDate(), job.getEndDate());
			BacktestCalculationResult calculation = engine.execute(job.getStrategyType(), priceData);
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
}
