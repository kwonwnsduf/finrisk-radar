package com.finrisk.radar.document.service;

import com.finrisk.radar.document.*;
import com.finrisk.radar.document.api.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentQueryService {
  private final DocumentRepository documents;
  private final DocumentAssetMappingRepository mappings;
  private final DocumentRiskMatchRepository matches;

  public DocumentQueryService(
      DocumentRepository documents,
      DocumentAssetMappingRepository mappings,
      DocumentRiskMatchRepository matches) {
    this.documents = documents;
    this.mappings = mappings;
    this.matches = matches;
  }

  @Transactional(readOnly = true)
  public List<DocumentResponse> list(
      Long assetId,
      DocumentSourceType source,
      DocumentType type,
      Boolean riskOnly,
      java.time.LocalDate from,
      java.time.LocalDate to,
      Long cursor,
      int size) {
    Set<Long> allowed =
        assetId == null
            ? null
            : new HashSet<>(
                mappings.findByAssetIdOrderByDocumentIdDesc(assetId).stream()
                    .map(DocumentAssetMapping::getDocumentId)
                    .toList());
    return documents.findAllByOrderByPublishedAtDescIdDesc().stream()
        .filter(d -> allowed == null || allowed.contains(d.getId()))
        .filter(d -> source == null || d.getSourceType() == source)
        .filter(d -> type == null || d.getDocumentType() == type)
        .filter(
            d ->
                from == null
                    || d.getPublishedAt() == null
                    || !d.getPublishedAt().toLocalDate().isBefore(from))
        .filter(
            d ->
                to == null
                    || d.getPublishedAt() == null
                    || !d.getPublishedAt().toLocalDate().isAfter(to))
        .filter(d -> cursor == null || d.getId() < cursor)
        .filter(
            d ->
                !Boolean.TRUE.equals(riskOnly)
                    || !matches
                        .findByDocumentIdOrderBySentenceIndexAscMatchStartOffsetAsc(d.getId())
                        .isEmpty())
        .limit(Math.max(1, Math.min(100, size)))
        .map(this::response)
        .toList();
  }

  @Transactional(readOnly = true)
  public DocumentResponse get(Long id) {
    return response(documents.findById(id).orElseThrow());
  }

  @Transactional(readOnly = true)
  public List<DocumentRiskMatchResponse> matchResponses(Long id) {
    if (!documents.existsById(id)) throw new NoSuchElementException();
    return matches.findByDocumentIdOrderBySentenceIndexAscMatchStartOffsetAsc(id).stream()
        .map(DocumentRiskMatchResponse::from)
        .toList();
  }

  private DocumentResponse response(Document d) {
    List<Long> ids =
        mappings.findByDocumentId(d.getId()).stream()
            .map(DocumentAssetMapping::getAssetId)
            .toList();
    return DocumentResponse.from(
        d,
        ids,
        matches.findByDocumentIdOrderBySentenceIndexAscMatchStartOffsetAsc(d.getId()).size());
  }
}
