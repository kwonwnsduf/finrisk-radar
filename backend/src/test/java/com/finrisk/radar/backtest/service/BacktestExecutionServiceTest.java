package com.finrisk.radar.backtest.service;

import com.finrisk.radar.backtest.*;
import com.finrisk.radar.backtest.engine.BacktestCalculationResult;
import com.finrisk.radar.backtest.engine.BacktestEngine;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.marketprice.MarketPriceResponse;
import com.finrisk.radar.marketprice.MarketPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BacktestExecutionServiceTest {
	@Mock BacktestJobService jobs;
	@Mock MarketPriceService prices;
	@Mock BacktestEngine engine;
	private BacktestExecutionService service;
	private BacktestJob job;

	@BeforeEach void setUp() {
		service = new BacktestExecutionService(jobs, prices, engine);
		job = BacktestJob.requested(1L, 2L, StrategyType.BUY_AND_HOLD,
				LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"));
	}

	@Test
	void loadsPricesCalculatesAndCompletesTheJob() {
		List<MarketPriceResponse> data = List.of();
		BacktestCalculationResult result = new BacktestCalculationResult(
				LocalDate.parse("2024-01-02"), LocalDate.parse("2024-12-30"),
				BigDecimal.valueOf(100), BigDecimal.valueOf(110), BigDecimal.TEN);
		when(jobs.markRunning(job.getJobId())).thenReturn(true);
		when(jobs.getInternal(job.getJobId())).thenReturn(job);
		when(prices.getPrices(2L, job.getStartDate(), job.getEndDate())).thenReturn(data);
		when(engine.execute(StrategyType.BUY_AND_HOLD, data)).thenReturn(result);

		service.execute(job.getJobId());

		verify(jobs).completeWithResult(job.getJobId(), result);
	}

	@Test
	void ignoresAJobThatCannotBeClaimed() {
		when(jobs.markRunning(job.getJobId())).thenReturn(false);
		service.execute(job.getJobId());
		verify(jobs, never()).getInternal(any());
		verifyNoInteractions(prices, engine);
	}

	@Test
	void marksAClaimedJobFailedWhenCalculationFails() {
		when(jobs.markRunning(job.getJobId())).thenReturn(true);
		when(jobs.getInternal(job.getJobId())).thenReturn(job);
		when(prices.getPrices(2L, job.getStartDate(), job.getEndDate()))
				.thenThrow(new BusinessException(ErrorCode.BACKTEST_PRICE_DATA_NOT_FOUND));

		assertThatThrownBy(() -> service.execute(job.getJobId())).isInstanceOf(BusinessException.class);
		verify(jobs).markFailed(job.getJobId(), ErrorCode.BACKTEST_PRICE_DATA_NOT_FOUND.getMessage());
	}
}
