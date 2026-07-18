package com.finrisk.radar.document.service;

import com.finrisk.radar.document.*;
import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.api.CreditEventCreateRequest;
import com.finrisk.radar.risk.service.RiskAdminService;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditEventReviewService {
  private final CreditEventCandidateRepository candidates;
  private final DocumentRiskMatchRepository matches;
  private final RiskAdminService risks;
  private final ApplicationEventPublisher events;

  public CreditEventReviewService(
      CreditEventCandidateRepository candidates,
      DocumentRiskMatchRepository matches,
      RiskAdminService risks,
      ApplicationEventPublisher events) {
    this.candidates = candidates;
    this.matches = matches;
    this.risks = risks;
    this.events = events;
  }

  @Transactional
  public CreditEventCandidate approve(Long id, Long reviewer, String note) {
    CreditEventCandidate c = find(id);
    if (c.getStatus() != CreditEventCandidateStatus.PENDING_REVIEW)
      throw new IllegalStateException("Candidate is already reviewed.");
    List<DocumentRiskMatch> evidence = matches.findByCandidateIdOrderByConfidenceDesc(id);
    String description =
        evidence.isEmpty()
            ? "Document-derived credit event"
            : evidence.get(0).getSentenceText()
                + (evidence.get(0).getAmountOriginalText() == null
                    ? ""
                    : " [amount: " + evidence.get(0).getAmountOriginalText() + "]");
    CreditEvent event =
        risks.createEvent(
            c.getAssetId(),
            new CreditEventCreateRequest(
                c.getEventType(),
                c.getEventDate(),
                c.getSeverity(),
                "DOCUMENT",
                "Day11 document risk detection",
                String.valueOf(evidence.isEmpty() ? null : evidence.get(0).getDocumentId()),
                description,
                c.getIncidentKey(),
                "DOCUMENT_CANDIDATE:" + id));
    c.approve(reviewer, note, event.getId());
    events.publishEvent(new CandidateApprovedNotification(c.getId(), c.getAssetId(), reviewer));
    return c;
  }

  @Transactional
  public CreditEventCandidate reject(Long id, Long reviewer, String note) {
    CreditEventCandidate c = find(id);
    if (c.getStatus() != CreditEventCandidateStatus.PENDING_REVIEW)
      throw new IllegalStateException("Candidate is already reviewed.");
    c.reject(reviewer, note);
    return c;
  }

  private CreditEventCandidate find(Long id) {
    return candidates
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Credit event candidate not found."));
  }
}
