package com.finrisk.radar.collector.service;

import com.finrisk.radar.collector.client.MarketDataClient;
import com.finrisk.radar.collector.client.MarketDataClientException;
import com.finrisk.radar.collector.client.RawMarketData;
import com.finrisk.radar.collector.log.CollectionLog;
import com.finrisk.radar.collector.log.CollectionLogService;
import com.finrisk.radar.collector.storage.RawMarketDataStorage;
import com.finrisk.radar.collector.storage.RawStorageException;
import com.finrisk.radar.marketprice.MarketPriceWriter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MarketPriceCollectionService {
	private final CollectionLogService logs;
	private final MarketDataClient client;
	private final RawMarketDataStorage storage;
	private final MarketDataNormalizer normalizer;
	private final MarketPriceWriter writer;
	public MarketPriceCollectionService(CollectionLogService logs, MarketDataClient client,
			RawMarketDataStorage storage, MarketDataNormalizer normalizer, MarketPriceWriter writer) {
		this.logs = logs; this.client = client; this.storage = storage; this.normalizer = normalizer; this.writer = writer;
	}
	public CollectionResult collect(UUID jobId) {
		if (!logs.markRunning(jobId)) return null;
		CollectionLog log = logs.getInternal(jobId);
		try {
			RawMarketData raw = client.fetch(log.getTicker(), log.getStartDate(), log.getEndDate());
			String path = storage.store(log.getAssetId(), log.getTicker(), log.getStartDate(), log.getEndDate(), raw);
			logs.markRawStored(jobId, raw.source(), path);
			List<PriceBar> bars = normalizer.normalize(raw, log.getStartDate(), log.getEndDate());
			writer.upsert(log.getAssetId(), raw.source(), bars);
			logs.markCompleted(jobId, raw.source(), bars.size());
			return new CollectionResult(jobId, log.getAssetId(), log.getTicker(), raw.source(), bars.size(), path);
		} catch (RuntimeException exception) {
			logs.markFailed(jobId, safeMessage(exception));
			throw exception;
		}
	}
	private String safeMessage(RuntimeException exception) {
		if (exception instanceof MarketDataClientException || exception instanceof RawStorageException)
			return exception.getMessage();
		return "Collection failed while processing market data.";
	}
}
