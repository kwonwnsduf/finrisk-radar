package com.finrisk.radar.backtest.service;

import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.backtest.*;
import com.finrisk.radar.backtest.api.BacktestCreateRequest;
import com.finrisk.radar.backtest.api.BacktestCreateResponse;
import com.finrisk.radar.backtest.event.BacktestRequestedEvent;
import com.finrisk.radar.backtest.kafka.BacktestEventPublishException;
import com.finrisk.radar.backtest.kafka.BacktestEventPublisher;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BacktestRequestServiceTest {
	@Mock UserRepository users;
	@Mock AssetRepository assets;
	@Mock BacktestJobService jobs;
	@Mock BacktestEventPublisher publisher;
	private BacktestRequestService service;

	@BeforeEach void setUp() { service = new BacktestRequestService(users, assets, jobs, publisher); }

	@Test
	void createsAJobAndPublishesItsId() {
		BacktestCreateRequest request = request();
		BacktestJob job = BacktestJob.requested(1L, 2L, StrategyType.BUY_AND_HOLD,
				request.startDate(), request.endDate());
		when(users.existsById(1L)).thenReturn(true);
		when(assets.existsById(2L)).thenReturn(true);
		when(jobs.createRequested(1L, 2L, StrategyType.BUY_AND_HOLD,
				request.startDate(), request.endDate())).thenReturn(job);

		BacktestCreateResponse response = service.request(1L, request);

		ArgumentCaptor<BacktestRequestedEvent> event = ArgumentCaptor.forClass(BacktestRequestedEvent.class);
		verify(publisher).publishRequestedAndAwait(event.capture());
		assertThat(event.getValue().jobId()).isEqualTo(job.getJobId());
		assertThat(response.status()).isEqualTo(BacktestStatus.REQUESTED);
	}

	@Test
	void marksTheJobFailedWhenPublishingFails() {
		BacktestCreateRequest request = request();
		BacktestJob job = BacktestJob.requested(1L, 2L, StrategyType.BUY_AND_HOLD,
				request.startDate(), request.endDate());
		when(users.existsById(1L)).thenReturn(true);
		when(assets.existsById(2L)).thenReturn(true);
		when(jobs.createRequested(any(), any(), any(), any(), any())).thenReturn(job);
		doThrow(new BacktestEventPublishException("publish failed", new RuntimeException()))
				.when(publisher).publishRequestedAndAwait(any());

		assertThatThrownBy(() -> service.request(1L, request))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.BACKTEST_REQUEST_FAILED);
		verify(jobs).markFailed(job.getJobId(), "publish failed");
	}

	@Test
	void rejectsAnInvertedRangeBeforeCreatingAJob() {
		BacktestCreateRequest request = new BacktestCreateRequest(2L, StrategyType.BUY_AND_HOLD,
				LocalDate.parse("2024-12-31"), LocalDate.parse("2024-01-01"));
		assertThatThrownBy(() -> service.request(1L, request)).isInstanceOf(BusinessException.class);
		verifyNoInteractions(jobs, publisher);
	}

	private BacktestCreateRequest request() {
		return new BacktestCreateRequest(2L, StrategyType.BUY_AND_HOLD,
				LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"));
	}
}
