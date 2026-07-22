package com.finrisk.radar.document;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {
  Optional<Document> findBySourceTypeAndExternalId(DocumentSourceType source, String externalId);

  List<Document> findAllByOrderByPublishedAtDescIdDesc();

  @Query(
      """
      select d from Document d
      where (:source is null or d.sourceType = :source)
        and (:from is null or d.publishedAt >= :from)
        and (:to is null or d.publishedAt < :to)
        and (:assetId is null or d.id in (
          select m.documentId from DocumentAssetMapping m where m.assetId = :assetId))
      order by d.id
      """)
  List<Document> findEmbeddingRebuildCandidates(
      @Param("source") DocumentSourceType source,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("assetId") Long assetId,
      Pageable pageable);
}
