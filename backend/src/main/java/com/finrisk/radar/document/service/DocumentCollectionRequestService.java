package com.finrisk.radar.document.service;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.document.*;
import com.finrisk.radar.document.event.DocumentFetchRequestedEvent;
import com.finrisk.radar.document.kafka.DocumentEventPublisher;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DocumentCollectionRequestService {
  private final AssetRepository assets;
  private final DocumentCollectionJobService jobs;
  private final DocumentEventPublisher publisher;

  public DocumentCollectionRequestService(
      AssetRepository assets, DocumentCollectionJobService jobs, DocumentEventPublisher publisher) {
    this.assets = assets;
    this.jobs = jobs;
    this.publisher = publisher;
  }

  public List<DocumentCollectionJob> request(
      Long user,
      Collection<Long> assetIds,
      Collection<DocumentSourceType> sources,
      LocalDate from,
      LocalDate to) {
    if (from == null || to == null || to.isBefore(from))
      throw new IllegalArgumentException("Invalid document collection date range.");
    List<DocumentCollectionJob> result = new ArrayList<>();
    for (Long assetId : assetIds) {
      Asset asset = assets.findById(assetId).orElseThrow();
      if (asset.getAssetType() != AssetType.BOND_ISSUER && asset.getAssetType() != AssetType.REIT)
        continue;
      for (DocumentSourceType source : sources) {
        DocumentCollectionJob job = jobs.create(user, assetId, source, from, to);
        try {
          publisher.requested(
              new DocumentFetchRequestedEvent(
                  1, UUID.randomUUID(), job.getJobId(), assetId, source, from, to, Instant.now()));
          result.add(job);
        } catch (RuntimeException e) {
          jobs.fail(job.getJobId(), "DOCUMENT_KAFKA_PUBLISH_FAILED", e.getMessage());
          throw e;
        }
      }
    }
    return result;
  }
}
