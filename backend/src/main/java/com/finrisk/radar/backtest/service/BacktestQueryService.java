package com.finrisk.radar.backtest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.backtest.*;
import com.finrisk.radar.backtest.api.BacktestJobResponse;
import com.finrisk.radar.backtest.api.BacktestResultResponse;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BacktestQueryService {
	private final BacktestJobRepository jobs;
	private final BacktestResultRepository results;
	private final ObjectMapper objectMapper;

	@Autowired
	public BacktestQueryService(BacktestJobRepository jobs, BacktestResultRepository results, ObjectMapper objectMapper) {
		this.jobs = jobs;
		this.results = results;
		this.objectMapper = objectMapper;
	}

	BacktestQueryService(BacktestJobRepository jobs, BacktestResultRepository results) {
		this(jobs, results, new ObjectMapper());
	}

	@Transactional(readOnly = true)
	public BacktestJobResponse getForUser(UUID jobId, Long userId, Role role) {
		BacktestJob job = jobs.findById(jobId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BACKTEST_JOB_NOT_FOUND));
		if (role != Role.ROLE_ADMIN && !job.getRequestedByUserId().equals(userId))
			throw new BusinessException(ErrorCode.BACKTEST_JOB_FORBIDDEN);
		BacktestResultResponse result = results.findById(jobId)
				.map(backtestResult -> BacktestResultResponse.from(backtestResult, objectMapper)).orElse(null);
		return BacktestJobResponse.from(job, result);
	}
}
