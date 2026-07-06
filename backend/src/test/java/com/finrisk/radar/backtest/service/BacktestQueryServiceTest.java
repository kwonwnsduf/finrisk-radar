package com.finrisk.radar.backtest.service;

import com.finrisk.radar.backtest.*;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacktestQueryServiceTest {
	@Mock BacktestJobRepository jobs;
	@Mock BacktestResultRepository results;
	private BacktestQueryService service;
	private BacktestJob job;

	@BeforeEach void setUp() {
		service = new BacktestQueryService(jobs, results);
		job = BacktestJob.requested(1L, 2L, StrategyType.BUY_AND_HOLD,
				LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"));
	}

	@Test
	void returnsAUsersOwnPendingJobWithoutAResult() {
		when(jobs.findById(job.getJobId())).thenReturn(Optional.of(job));
		when(results.findById(job.getJobId())).thenReturn(Optional.empty());

		var response = service.getForUser(job.getJobId(), 1L, Role.ROLE_USER);

		assertThat(response.status()).isEqualTo(BacktestStatus.REQUESTED);
		assertThat(response.result()).isNull();
	}

	@Test
	void rejectsAnotherUsersJob() {
		when(jobs.findById(job.getJobId())).thenReturn(Optional.of(job));

		assertThatThrownBy(() -> service.getForUser(job.getJobId(), 99L, Role.ROLE_USER))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(ErrorCode.BACKTEST_JOB_FORBIDDEN);
	}

	@Test
	void allowsAnAdministratorToReadAnotherUsersJob() {
		when(jobs.findById(job.getJobId())).thenReturn(Optional.of(job));
		when(results.findById(job.getJobId())).thenReturn(Optional.empty());

		assertThat(service.getForUser(job.getJobId(), 99L, Role.ROLE_ADMIN).jobId())
				.isEqualTo(job.getJobId());
	}
}
