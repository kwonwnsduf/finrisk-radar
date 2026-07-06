package com.finrisk.radar.backtest;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestJobTest {
	@Test
	void transitionsFromRequestedToRunningToCompleted() {
		BacktestJob job = job();

		assertThat(job.getStatus()).isEqualTo(BacktestStatus.REQUESTED);
		assertThat(job.start()).isTrue();
		job.complete();

		assertThat(job.getStatus()).isEqualTo(BacktestStatus.COMPLETED);
		assertThat(job.getStartedAt()).isNotNull();
		assertThat(job.getCompletedAt()).isNotNull();
	}

	@Test
	void doesNotStartTheSameJobTwice() {
		BacktestJob job = job();
		assertThat(job.start()).isTrue();
		assertThat(job.start()).isFalse();
	}

	@Test
	void onlyRunningJobsCanComplete() {
		assertThatThrownBy(() -> job().complete()).isInstanceOf(IllegalStateException.class);
	}

	private BacktestJob job() {
		return BacktestJob.requested(1L, 2L, StrategyType.BUY_AND_HOLD,
				LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"));
	}
}
