package com.finrisk.radar.financial;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.financial.event.FinancialDataFetchRequestedEvent;
import com.finrisk.radar.financial.kafka.FinancialDataEventPublishException;
import com.finrisk.radar.financial.kafka.FinancialDataEventPublisher;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class FinancialDataRequestService {
	private final AssetRepository assets;
	private final UserRepository users;
	private final FinancialCollectionLogService logs;
	private final FinancialDataEventPublisher publisher;

	public FinancialDataRequestService(AssetRepository assets, UserRepository users,
			FinancialCollectionLogService logs, FinancialDataEventPublisher publisher) {
		this.assets = assets; this.users = users; this.logs = logs; this.publisher = publisher;
	}

	public FinancialMetricFetchResponse request(Long userId, FinancialMetricFetchRequest request) {
		if (!users.existsById(userId)) throw new BusinessException(ErrorCode.UNAUTHORIZED);
		if (request.year() == null || request.quarter() == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
		Asset asset = resolveAsset(request);
		String stockCode = stockCode(request, asset);
		FinancialCollectionLog log = logs.createRequested(userId, asset.getId(), stockCode, request.year(), request.quarter());
		try {
			publisher.publishRequestedAndAwait(new FinancialDataFetchRequestedEvent(log.getJobId(), asset.getId(),
					stockCode, request.year(), request.quarter(), Instant.now()));
		} catch (FinancialDataEventPublishException exception) {
			logs.markFailed(log.getJobId(), exception.getMessage());
			throw new BusinessException(ErrorCode.FINANCIAL_COLLECTION_REQUEST_FAILED);
		}
		return new FinancialMetricFetchResponse(log.getJobId(), log.getStatus());
	}

	private Asset resolveAsset(FinancialMetricFetchRequest request) {
		boolean hasAssetId = request.assetId() != null;
		boolean hasStockCode = request.stockCode() != null && !request.stockCode().isBlank();
		if (!hasAssetId && !hasStockCode) throw new BusinessException(ErrorCode.INVALID_INPUT);
		if (hasAssetId) {
			Asset asset = assets.findById(request.assetId()).orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
			if (hasStockCode && !asset.getTicker().equals(request.stockCode().trim()))
				throw new BusinessException(ErrorCode.INVALID_INPUT);
			return asset;
		}
		return assets.findFirstByTickerOrderByIdAsc(request.stockCode().trim())
				.orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
	}

	private String stockCode(FinancialMetricFetchRequest request, Asset asset) {
		return request.stockCode() == null || request.stockCode().isBlank() ? asset.getTicker() : request.stockCode().trim();
	}
}
