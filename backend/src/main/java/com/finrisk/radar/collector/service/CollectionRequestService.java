package com.finrisk.radar.collector.service;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.collector.api.MarketPriceFetchRequest;
import com.finrisk.radar.collector.api.MarketPriceFetchResponse;
import com.finrisk.radar.collector.event.MarketDataFetchRequestedEvent;
import com.finrisk.radar.collector.kafka.EventPublishException;
import com.finrisk.radar.collector.kafka.MarketDataEventPublisher;
import com.finrisk.radar.collector.log.CollectionLog;
import com.finrisk.radar.collector.log.CollectionLogService;
import com.finrisk.radar.collector.log.CollectionStatus;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CollectionRequestService {
	private final AssetRepository assets;
	private final UserRepository users;
	private final MarketTickerResolver tickerResolver;
	private final CollectionLogService logs;
	private final MarketDataEventPublisher publisher;
	public CollectionRequestService(AssetRepository assets, UserRepository users, MarketTickerResolver tickerResolver,
			CollectionLogService logs, MarketDataEventPublisher publisher) {
		this.assets = assets; this.users = users; this.tickerResolver = tickerResolver; this.logs = logs; this.publisher = publisher;
	}
	public MarketPriceFetchResponse request(Long userId, MarketPriceFetchRequest request) {
		if (request.startDate().isAfter(request.endDate())) throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
		if (!users.existsById(userId)) throw new BusinessException(ErrorCode.UNAUTHORIZED);
		Asset asset = assets.findById(request.assetId()).orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
		String ticker = tickerResolver.validate(asset, request.ticker());
		CollectionLog log = logs.createRequested(userId, asset.getId(), ticker, request.startDate(), request.endDate());
		try {
			publisher.publishRequestedAndAwait(new MarketDataFetchRequestedEvent(log.getJobId(), asset.getId(), ticker,
					request.startDate(), request.endDate(), Instant.now()));
		} catch (EventPublishException exception) {
			logs.markFailed(log.getJobId(), exception.getMessage());
			throw new BusinessException(ErrorCode.COLLECTION_REQUEST_FAILED);
		}
		return new MarketPriceFetchResponse(log.getJobId(), CollectionStatus.REQUESTED);
	}
}
