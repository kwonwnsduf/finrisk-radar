package com.finrisk.radar.document.service;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.document.DocumentSourceType;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.documents.scheduler", name = "enabled", havingValue = "true")
public class DocumentCollectionScheduler {
  private static final Logger log = LoggerFactory.getLogger(DocumentCollectionScheduler.class);
  private final AssetRepository assets;
  private final DocumentCollectionRequestService requests;

  public DocumentCollectionScheduler(
      AssetRepository assets, DocumentCollectionRequestService requests) {
    this.assets = assets;
    this.requests = requests;
  }

  @Scheduled(cron = "${app.documents.scheduler.cron:0 0 * * * *}")
  public void collect() {
    LocalDate today = LocalDate.now();
    for (Asset a : assets.findAll())
      if (a.getAssetType() == AssetType.BOND_ISSUER || a.getAssetType() == AssetType.REIT)
        try {
          requests.request(
              null,
              List.of(a.getId()),
              List.of(DocumentSourceType.NAVER_NEWS, DocumentSourceType.OPEN_DART),
              today.minusDays(1),
              today);
        } catch (RuntimeException e) {
          log.error(
              "Scheduled document collection request failed: assetId={}, ticker={}",
              a.getId(),
              a.getTicker(),
              e);
        }
  }
}
