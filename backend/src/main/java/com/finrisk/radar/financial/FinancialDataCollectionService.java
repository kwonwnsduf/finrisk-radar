package com.finrisk.radar.financial;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.collector.storage.RawStorageException;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FinancialDataCollectionService {
  private final FinancialCollectionLogService logs;
  private final AssetRepository assets;
  private final DartCorpCodeService corpCodes;
  private final DartClient client;
  private final FinancialRawStorage storage;
  private final DartFinancialStatementNormalizer normalizer;
  private final FinancialMetricService metrics;

  public FinancialDataCollectionService(
      FinancialCollectionLogService logs,
      AssetRepository assets,
      DartCorpCodeService corpCodes,
      DartClient client,
      FinancialRawStorage storage,
      DartFinancialStatementNormalizer normalizer,
      FinancialMetricService metrics) {
    this.logs = logs;
    this.assets = assets;
    this.corpCodes = corpCodes;
    this.client = client;
    this.storage = storage;
    this.normalizer = normalizer;
    this.metrics = metrics;
  }

  public FinancialCollectionResult collect(UUID jobId) {
    if (!logs.markRunning(jobId)) return null;
    FinancialCollectionLog log = logs.getInternal(jobId);
    try {
      Asset asset =
          assets
              .findById(log.getAssetId())
              .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
      String corpCode = corpCodes.findCorpCode(asset);
      RawDartFinancialStatement raw =
          client.fetchLatestFinancialStatement(corpCode, log.getYear(), log.getQuarter(), 7);
      String path =
          storage.storeFinancialStatement(log.getAssetId(), log.getStockCode(), corpCode, raw);
      logs.markRawStored(jobId, corpCode, raw.division().name(), raw.fallbackUsed(), path);
      FinancialMetricValues values = normalizer.normalize(raw.payload());
      metrics.upsertCollected(asset, raw.year(), raw.quarter(), values);
      logs.markCompleted(jobId, 1);
      return new FinancialCollectionResult(
          jobId,
          log.getAssetId(),
          log.getStockCode(),
          corpCode,
          raw.year(),
          raw.quarter(),
          raw.division().name(),
          raw.fallbackUsed(),
          1,
          path);
    } catch (RuntimeException exception) {
      logs.markFailed(jobId, safeMessage(exception));
      throw exception;
    }
  }

  private String safeMessage(RuntimeException exception) {
    if (exception instanceof BusinessException businessException)
      return businessException.getErrorCode().getMessage();
    if (exception instanceof DartClientException || exception instanceof RawStorageException)
      return exception.getMessage();
    return "Financial data collection failed while processing DART data.";
  }
}
