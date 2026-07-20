package com.finrisk.radar.document.service;

import com.finrisk.radar.document.*;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DocumentCollectionJobService {
  private final DocumentCollectionJobRepository jobs;

  public DocumentCollectionJobService(DocumentCollectionJobRepository jobs) {
    this.jobs = jobs;
  }

  @Transactional
  public DocumentCollectionJob create(
      Long user,
      Long asset,
      DocumentSourceType source,
      java.time.LocalDate from,
      java.time.LocalDate to) {
    return jobs.save(DocumentCollectionJob.requested(user, asset, source, from, to));
  }

  @Transactional
  public boolean start(UUID id) {
    return get(id).start();
  }

  @Transactional
  public void complete(UUID id, int count) {
    get(id).complete(count);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void fail(UUID id, String code, String message) {
    String safe = message == null ? "Document collection failed." : message;
    get(id).fail(code, safe.substring(0, Math.min(1000, safe.length())));
  }

  @Transactional(readOnly = true)
  public DocumentCollectionJob get(UUID id) {
    return jobs.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Document collection job not found."));
  }

  @Transactional(readOnly = true)
  public List<DocumentCollectionJob> recent(Long assetId) {
    return assetId == null
        ? jobs.findTop50ByOrderByRequestedAtDesc()
        : jobs.findTop50ByAssetIdOrderByRequestedAtDesc(assetId);
  }
}
