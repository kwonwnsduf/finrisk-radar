package com.finrisk.radar.rag;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DocumentEmbeddingJobRepository extends JpaRepository<DocumentEmbeddingJob, UUID> {
  Optional<DocumentEmbeddingJob>
      findByDocumentIdAndContentVersionAndEmbeddingModelAndEmbeddingDimensions(
          Long documentId, int contentVersion, String model, int dimensions);

  Optional<DocumentEmbeddingJob> findTopByDocumentIdOrderByRequestedAtDesc(Long documentId);

  List<DocumentEmbeddingJob> findByDocumentIdOrderByRequestedAtDesc(Long documentId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update DocumentEmbeddingJob j set j.active = false where j.documentId = :documentId and"
          + " j.active = true")
  int deactivateByDocumentId(@Param("documentId") Long documentId);
}
