package com.finrisk.radar.backtest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.backtest.BacktestStrategyConfig;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.backtest.BacktestJob;
import com.finrisk.radar.backtest.BacktestJobService;
import com.finrisk.radar.backtest.CustomConditionType;
import com.finrisk.radar.backtest.StrategyType;
import com.finrisk.radar.backtest.api.BacktestCreateRequest;
import com.finrisk.radar.backtest.api.BacktestCreateResponse;
import com.finrisk.radar.backtest.api.StrategyCondition;
import com.finrisk.radar.backtest.event.BacktestRequestedEvent;
import com.finrisk.radar.backtest.kafka.BacktestEventPublishException;
import com.finrisk.radar.backtest.kafka.BacktestEventPublisher;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import com.finrisk.radar.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BacktestRequestService {
	private final UserRepository users;
	private final AssetRepository assets;
	private final BacktestJobService jobs;
	private final BacktestEventPublisher publisher;
	private final ObjectMapper objectMapper;

	@Autowired
	public BacktestRequestService(UserRepository users, AssetRepository assets,
			BacktestJobService jobs, BacktestEventPublisher publisher, ObjectMapper objectMapper) {
		this.users = users;
		this.assets = assets;
		this.jobs = jobs;
		this.publisher = publisher;
		this.objectMapper = objectMapper;
	}

	BacktestRequestService(UserRepository users, AssetRepository assets,
			BacktestJobService jobs, BacktestEventPublisher publisher) {
		this(users, assets, jobs, publisher, new ObjectMapper());
	}

	public BacktestCreateResponse request(Long userId, BacktestCreateRequest request) {
		if (request.startDate().isAfter(request.endDate())) throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
		if (!users.existsById(userId)) throw new BusinessException(ErrorCode.UNAUTHORIZED);
		if (!assets.existsById(request.assetId())) throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
		validate(request);

		BacktestJob job = shouldUseLegacyCreate(request)
				? jobs.createRequested(userId, request.assetId(), request.strategyType(), request.startDate(), request.endDate())
				: jobs.createRequested(userId, request.assetId(), request.strategyType(),
						request.startDate(), request.endDate(), request.initialCash(), serializeConfig(request));
		try {
			publisher.publishRequestedAndAwait(new BacktestRequestedEvent(job.getJobId(), Instant.now()));
		} catch (BacktestEventPublishException exception) {
			jobs.markFailed(job.getJobId(), exception.getMessage());
			throw new BusinessException(ErrorCode.BACKTEST_REQUEST_FAILED);
		}
		return new BacktestCreateResponse(job.getJobId(), job.getStatus());
	}

	private void validate(BacktestCreateRequest request) {
		if (request.initialCash().signum() <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
		if (request.strategyType() != StrategyType.CUSTOM) return;
		if (request.buyConditions().isEmpty() || request.sellConditions().isEmpty())
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		request.buyConditions().forEach(condition -> validateCondition(condition, true));
		request.sellConditions().forEach(condition -> validateCondition(condition, false));
	}

	private void validateCondition(StrategyCondition condition, boolean buySide) {
		if (condition == null || condition.type() == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
		if (buySide && !isBuyCondition(condition.type())) throw new BusinessException(ErrorCode.INVALID_INPUT);
		if (!buySide && !isSellCondition(condition.type())) throw new BusinessException(ErrorCode.INVALID_INPUT);
		switch (condition.type()) {
			case RSI_LESS_THAN, RSI_GREATER_THAN, STOP_LOSS, TAKE_PROFIT, TRAILING_STOP -> requireValue(condition);
			case PRICE_ABOVE_MA, MA_CROSS_UP, BOLLINGER_LOWER_TOUCH, VOLUME_SPIKE, BREAKOUT,
					MA_DISCOUNT, DONCHIAN_HIGH_BREAKOUT, MOMENTUM_POSITIVE, PRICE_BELOW_MA,
					MA_CROSS_DOWN, BOLLINGER_UPPER_TOUCH, MA_PREMIUM, DONCHIAN_LOW_BREAKDOWN,
					MOMENTUM_NEGATIVE -> requirePeriod(condition);
			case MACD_GOLDEN_CROSS, MACD_DEAD_CROSS -> {}
		}
	}

	private boolean isBuyCondition(CustomConditionType type) {
		return switch (type) {
			case RSI_LESS_THAN, PRICE_ABOVE_MA, MA_CROSS_UP, BOLLINGER_LOWER_TOUCH,
					MACD_GOLDEN_CROSS, VOLUME_SPIKE, BREAKOUT, MA_DISCOUNT,
					DONCHIAN_HIGH_BREAKOUT, MOMENTUM_POSITIVE -> true;
			default -> false;
		};
	}

	private boolean isSellCondition(CustomConditionType type) {
		return !isBuyCondition(type);
	}

	private void requireValue(StrategyCondition condition) {
		if (condition.value() == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
	}

	private void requirePeriod(StrategyCondition condition) {
		if (condition.period() == null || condition.period() <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
	}

	private String serializeConfig(BacktestCreateRequest request) {
		BacktestStrategyConfig config = new BacktestStrategyConfig(request.buyConditions(), request.sellConditions());
		try {
			return objectMapper.writeValueAsString(config);
		} catch (JsonProcessingException exception) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	private boolean shouldUseLegacyCreate(BacktestCreateRequest request) {
		return request.initialCash().compareTo(BacktestCreateRequest.DEFAULT_INITIAL_CASH) == 0
				&& request.buyConditions().isEmpty() && request.sellConditions().isEmpty();
	}
}
