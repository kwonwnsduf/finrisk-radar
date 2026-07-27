package com.finrisk.radar.document;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;

public interface DocumentCollectionJobRepository
    extends JpaRepository<DocumentCollectionJob, UUID> {
  List<DocumentCollectionJob> findTop50ByOrderByRequestedAtDesc();

  List<DocumentCollectionJob> findTop50ByAssetIdOrderByRequestedAtDesc(Long assetId);

  long countByStatusAndCompletedAtAfter(DocumentCollectionStatus status, LocalDateTime after);

  Page<DocumentCollectionJob> findByStatusOrderByCompletedAtDesc(
      DocumentCollectionStatus status, Pageable pageable);
}
