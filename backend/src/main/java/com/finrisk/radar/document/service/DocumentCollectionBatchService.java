package com.finrisk.radar.document.service;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.asset.AssetType;
import com.finrisk.radar.document.DocumentSourceType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DocumentCollectionBatchService {
  private static final Logger log = LoggerFactory.getLogger(DocumentCollectionBatchService.class);
  private final AssetRepository assets;
  private final DocumentCollectionRequestService requests;
  private final Clock clock;

  @Autowired
  public DocumentCollectionBatchService(
      AssetRepository assets, DocumentCollectionRequestService requests) {
    this(assets, requests, Clock.systemDefaultZone());
  }

  DocumentCollectionBatchService(
      AssetRepository assets, DocumentCollectionRequestService requests, Clock clock) {
    this.assets = assets;
    this.requests = requests;
    this.clock = clock;
  }

  public Result collect() {
    LocalDate today = LocalDate.now(clock);
    int requested = 0;
    int failed = 0;
    for (Asset asset : assets.findAll()) {
      if (asset.getAssetType() != AssetType.BOND_ISSUER
          && asset.getAssetType() != AssetType.REIT) continue;
      try {
        requests.request(
            null,
            List.of(asset.getId()),
            List.of(DocumentSourceType.NAVER_NEWS, DocumentSourceType.OPEN_DART),
            today.minusDays(1),
            today);
        requested++;
      } catch (RuntimeException exception) {
        failed++;
        log.error(
            "Document collection request failed: assetId={}, ticker={}",
            asset.getId(),
            asset.getTicker(),
            exception);
      }
    }
    return new Result(requested, failed);
  }

  public record Result(int requested, int failed) {}
}
