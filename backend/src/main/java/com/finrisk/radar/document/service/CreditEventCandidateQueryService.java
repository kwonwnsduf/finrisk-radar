package com.finrisk.radar.document.service;

import com.finrisk.radar.admin.AdminPage;
import com.finrisk.radar.asset.*;
import com.finrisk.radar.document.*;
import com.finrisk.radar.document.api.*;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.risk.*;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditEventCandidateQueryService {
  private final CreditEventCandidateRepository candidates;
  private final DocumentRiskMatchRepository matches;
  private final DocumentRepository documents;
  private final AssetRepository assets;

  public CreditEventCandidateQueryService(
      CreditEventCandidateRepository candidates,
      DocumentRiskMatchRepository matches,
      DocumentRepository documents,
      AssetRepository assets) {
    this.candidates = candidates;
    this.matches = matches;
    this.documents = documents;
    this.assets = assets;
  }

  @Transactional(readOnly = true)
  public AdminPage<CandidateSummaryResponse> list(
      CreditEventCandidateStatus status,
      RiskSeverity severity,
      Long assetId,
      CreditEventType eventType,
      LocalDateTime from,
      LocalDateTime to,
      int page,
      int size) {
    Specification<CreditEventCandidate> specification =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (status != null) predicates.add(cb.equal(root.get("status"), status));
          if (severity != null) predicates.add(cb.equal(root.get("severity"), severity));
          if (assetId != null) predicates.add(cb.equal(root.get("assetId"), assetId));
          if (eventType != null) predicates.add(cb.equal(root.get("eventType"), eventType));
          if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
          if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    Page<CreditEventCandidate> result =
        candidates.findAll(
            specification,
            PageRequest.of(
                Math.max(0, page),
                Math.min(100, Math.max(1, size)),
                Sort.by("createdAt").descending()));
    Map<Long, Asset> assetById =
        assets.findAllById(result.stream().map(CreditEventCandidate::getAssetId).toList()).stream()
            .collect(Collectors.toMap(Asset::getId, Function.identity()));
    Map<Long, DocumentRiskMatch> representativeById =
        matches
            .findAllById(
                result.stream()
                    .map(CreditEventCandidate::getRepresentativeMatchId)
                    .filter(Objects::nonNull)
                    .toList())
            .stream()
            .collect(Collectors.toMap(DocumentRiskMatch::getId, Function.identity()));
    Map<Long, Document> documentById =
        documents
            .findAllById(
                representativeById.values().stream()
                    .map(DocumentRiskMatch::getDocumentId)
                    .toList())
            .stream()
            .collect(Collectors.toMap(Document::getId, Function.identity()));
    List<CandidateSummaryResponse> items =
        result.stream()
            .map(
                candidate -> {
                  Asset asset = assetById.get(candidate.getAssetId());
                  DocumentRiskMatch match =
                      representativeById.get(candidate.getRepresentativeMatchId());
                  Document document =
                      match == null ? null : documentById.get(match.getDocumentId());
                  return new CandidateSummaryResponse(
                      candidate.getId(),
                      candidate.getAssetId(),
                      asset == null ? null : asset.getName(),
                      asset == null ? null : asset.getTicker(),
                      candidate.getEventType(),
                      candidate.getEventDate(),
                      candidate.getSeverity(),
                      candidate.getConfidence(),
                      document == null ? null : document.getTitle(),
                      document == null ? null : document.getSourceType().name(),
                      candidate.getStatus(),
                      candidate.getCreatedAt());
                })
            .toList();
    return AdminPage.from(result, items);
  }

  @Transactional(readOnly = true)
  public CandidateDetailResponse get(Long id) {
    CreditEventCandidate candidate =
        candidates
            .findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_EVENT_CANDIDATE_NOT_FOUND));
    Asset asset =
        assets
            .findById(candidate.getAssetId())
            .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
    List<DocumentRiskMatch> evidence =
        matches.findByCandidateIdOrderByConfidenceDesc(candidate.getId());
    Map<Long, Document> documentById =
        documents.findAllById(evidence.stream().map(DocumentRiskMatch::getDocumentId).toList())
            .stream()
            .collect(Collectors.toMap(Document::getId, Function.identity()));
    List<CandidateDetailResponse.Match> matchResponses =
        evidence.stream()
            .map(
                match -> {
                  Document document = documentById.get(match.getDocumentId());
                  return new CandidateDetailResponse.Match(
                      match.getId(),
                      match.getDocumentId(),
                      document == null ? null : document.getTitle(),
                      document == null ? null : document.getSourceType().name(),
                      document == null ? null : document.getSourceName(),
                      document == null ? null : document.getSourceUrl(),
                      match.getSentenceText(),
                      match.getMatchedText(),
                      match.getAssertionType(),
                      match.getConfidence(),
                      match.getExtractedAmount(),
                      match.getExtractedCurrency(),
                      match.getEvidence());
                })
            .toList();
    List<CandidateDetailResponse.Nearby> nearby =
        candidates
            .findByAssetIdAndEventTypeAndEventDateBetween(
                candidate.getAssetId(),
                candidate.getEventType(),
                candidate.getEventDate().minusDays(7),
                candidate.getEventDate().plusDays(7))
            .stream()
            .filter(value -> !value.getId().equals(candidate.getId()))
            .map(
                value ->
                    new CandidateDetailResponse.Nearby(
                        value.getId(),
                        value.getEventDate(),
                        value.getSeverity(),
                        value.getConfidence(),
                        value.getStatus()))
            .toList();
    return new CandidateDetailResponse(
        candidate.getId(),
        candidate.getAssetId(),
        asset.getName(),
        asset.getTicker(),
        candidate.getEventType(),
        candidate.getEventDate(),
        candidate.getSeverity(),
        candidate.getConfidence(),
        candidate.getStatus(),
        candidate.getReviewedByUserId(),
        candidate.getReviewedAt(),
        candidate.getReviewNote(),
        candidate.getCreatedAt(),
        matchResponses,
        nearby);
  }
}
