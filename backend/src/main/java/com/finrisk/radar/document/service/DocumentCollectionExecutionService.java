package com.finrisk.radar.document.service;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.document.*;
import com.finrisk.radar.document.collector.*;
import com.finrisk.radar.financial.DartCorpCodeService;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DocumentCollectionExecutionService {
  private final DocumentCollectionJobService jobs;
  private final AssetRepository assets;
  private final DartCorpCodeService corpCodes;
  private final DocumentCollectorRegistry registry;
  private final DocumentPersistenceService persistence;
  private final DocumentAssetMappingService mapping;

  public DocumentCollectionExecutionService(
      DocumentCollectionJobService jobs,
      AssetRepository assets,
      DartCorpCodeService corpCodes,
      DocumentCollectorRegistry registry,
      DocumentPersistenceService persistence,
      DocumentAssetMappingService mapping) {
    this.jobs = jobs;
    this.assets = assets;
    this.corpCodes = corpCodes;
    this.registry = registry;
    this.persistence = persistence;
    this.mapping = mapping;
  }

  public List<Result> execute(java.util.UUID jobId) {
    if (!jobs.start(jobId)) return List.of();
    DocumentCollectionJob job = jobs.get(jobId);
    try {
      Asset asset = assets.findById(job.getAssetId()).orElseThrow();
      String corp =
          job.getSourceType() == DocumentSourceType.OPEN_DART
              ? corpCodes.findCorpCode(asset)
              : null;
      List<Result> results = new ArrayList<>();
      for (CollectedDocument raw :
          registry
              .get(job.getSourceType())
              .collect(
                  new DocumentCollectionContext(
                      jobId, asset, corp, job.getFromDate(), job.getToDate()))) {
        Document doc = persistence.persist(jobId, raw);
        List<DocumentAssetMapping> mapped = mapping.map(doc, asset, corp);
        results.add(
            new Result(doc, mapped.stream().map(DocumentAssetMapping::getAssetId).toList()));
      }
      jobs.complete(jobId, results.size());
      return results;
    } catch (RuntimeException e) {
      jobs.fail(jobId, "DOCUMENT_COLLECTION_FAILED", e.getMessage());
      throw e;
    }
  }

  public record Result(Document document, List<Long> assetIds) {}
}
