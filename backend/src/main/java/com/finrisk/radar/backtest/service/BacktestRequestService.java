package com.finrisk.radar.backtest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.backtest.BacktestJob;
import com.finrisk.radar.backtest.BacktestJobService;
import com.finrisk.radar.backtest.BacktestStrategyConfig;
import com.finrisk.radar.backtest.api.BacktestCreateRequest;
import com.finrisk.radar.backtest.api.BacktestCreateResponse;
import com.finrisk.radar.backtest.event.BacktestRequestedEvent;
import com.finrisk.radar.backtest.kafka.BacktestEventPublishException;
import com.finrisk.radar.backtest.kafka.BacktestEventPublisher;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.UserRepository;
import java.time.Instant;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BacktestRequestService {
  private final UserRepository users;
  private final AssetRepository assets;
  private final BacktestJobService jobs;
  private final BacktestEventPublisher publisher;
  private final ObjectMapper objectMapper;
  private final BacktestRequestValidator validator;

  @Autowired
  public BacktestRequestService(
      UserRepository users,
      AssetRepository assets,
      BacktestJobService jobs,
      BacktestEventPublisher publisher,
      ObjectMapper objectMapper,
      BacktestRequestValidator validator) {
    this.users = users;
    this.assets = assets;
    this.jobs = jobs;
    this.publisher = publisher;
    this.objectMapper = objectMapper;
    this.validator = validator;
  }

  BacktestRequestService(
      UserRepository users,
      AssetRepository assets,
      BacktestJobService jobs,
      BacktestEventPublisher publisher) {
    this(users, assets, jobs, publisher, new ObjectMapper(), new BacktestRequestValidator());
  }

  public BacktestCreateResponse request(Long userId, BacktestCreateRequest request) {
    return request(userId, request, job -> {});
  }

  public BacktestCreateResponse request(
      Long userId,
      BacktestCreateRequest request,
      Consumer<BacktestJob> afterJobCreated) {
    if (request.startDate().isAfter(request.endDate()))
      throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
    if (!users.existsById(userId)) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    if (!assets.existsById(request.assetId()))
      throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
    validator.validate(request);

    BacktestJob job =
        shouldUseLegacyCreate(request)
            ? jobs.createRequested(
                userId,
                request.assetId(),
                request.strategyType(),
                request.startDate(),
                request.endDate())
            : jobs.createRequested(
                userId,
                request.assetId(),
                request.strategyType(),
                request.startDate(),
                request.endDate(),
                request.initialCash(),
                serializeConfig(request));
    afterJobCreated.accept(job);
    try {
      publisher.publishRequestedAndAwait(new BacktestRequestedEvent(job.getJobId(), Instant.now()));
    } catch (BacktestEventPublishException exception) {
      jobs.markFailed(job.getJobId(), exception.getMessage());
      throw new BusinessException(ErrorCode.BACKTEST_REQUEST_FAILED);
    }
    return new BacktestCreateResponse(job.getJobId(), job.getStatus());
  }

  private String serializeConfig(BacktestCreateRequest request) {
    BacktestStrategyConfig config =
        new BacktestStrategyConfig(request.buyConditions(), request.sellConditions());
    try {
      return objectMapper.writeValueAsString(config);
    } catch (JsonProcessingException exception) {
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
  }

  private boolean shouldUseLegacyCreate(BacktestCreateRequest request) {
    return request.initialCash().compareTo(BacktestCreateRequest.DEFAULT_INITIAL_CASH) == 0
        && request.buyConditions().isEmpty()
        && request.sellConditions().isEmpty();
  }
}
