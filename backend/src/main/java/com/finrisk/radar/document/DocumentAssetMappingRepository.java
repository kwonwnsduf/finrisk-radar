package com.finrisk.radar.document;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentAssetMappingRepository extends JpaRepository<DocumentAssetMapping, Long> {
  List<DocumentAssetMapping> findByDocumentId(Long documentId);

  List<DocumentAssetMapping> findByDocumentIdIn(Collection<Long> documentIds);

  List<DocumentAssetMapping> findByAssetIdOrderByDocumentIdDesc(Long assetId);

  Optional<DocumentAssetMapping> findByDocumentIdAndAssetId(Long documentId, Long assetId);

  @Transactional
  void deleteByDocumentIdAndAssetId(Long documentId, Long assetId);
}
