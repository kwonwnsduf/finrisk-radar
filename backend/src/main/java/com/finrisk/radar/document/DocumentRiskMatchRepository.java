package com.finrisk.radar.document;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRiskMatchRepository extends JpaRepository<DocumentRiskMatch, Long> {
  List<DocumentRiskMatch> findByDocumentIdOrderBySentenceIndexAscMatchStartOffsetAsc(
      Long documentId);

  List<DocumentRiskMatch> findByCandidateIdOrderByConfidenceDesc(Long candidateId);

  boolean existsByDocumentIdAndAssetIdAndKeywordCodeAndSentenceIndexAndMatchStartOffset(
      Long document, Long asset, String keyword, int sentence, int start);
}
