package com.finrisk.radar.backtest.service;

import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.backtest.BacktestJob;
import com.finrisk.radar.backtest.BacktestJobService;
import com.finrisk.radar.backtest.api.BacktestCreateRequest;
import com.finrisk.radar.backtest.api.BacktestCreateResponse;
import com.finrisk.radar.backtest.event.BacktestRequestedEvent;
import com.finrisk.radar.backtest.kafka.BacktestEventPublishException;
import com.finrisk.radar.backtest.kafka.BacktestEventPublisher;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BacktestRequestService {
	private final UserRepository users;
	private final AssetRepository assets;
	private final BacktestJobService jobs;
	private final BacktestEventPublisher publisher;

	public BacktestRequestService(UserRepository users, AssetRepository assets,
			BacktestJobService jobs, BacktestEventPublisher publisher) {
		this.users = users;
		this.assets = assets;
		this.jobs = jobs;
		this.publisher = publisher;
	}

	public BacktestCreateResponse request(Long userId, BacktestCreateRequest request) {
		if (request.startDate().isAfter(request.endDate())) throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
		if (!users.existsById(userId)) throw new BusinessException(ErrorCode.UNAUTHORIZED);
		if (!assets.existsById(request.assetId())) throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);

		BacktestJob job = jobs.createRequested(userId, request.assetId(), request.strategyType(),
				request.startDate(), request.endDate());
		try {
			publisher.publishRequestedAndAwait(new BacktestRequestedEvent(job.getJobId(), Instant.now()));
		} catch (BacktestEventPublishException exception) {
			jobs.markFailed(job.getJobId(), exception.getMessage());
			throw new BusinessException(ErrorCode.BACKTEST_REQUEST_FAILED);
		}
		return new BacktestCreateResponse(job.getJobId(), job.getStatus());
	}
}
