package com.finrisk.radar.document.service;

import com.finrisk.radar.document.*;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.risk.service.RiskCalculationRequestService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;

@Component
public class DocumentRiskRecalculationCoordinator {
  private final CreditEventCandidateRepository candidates;
  private final RiskCalculationRequestService risks;

  public DocumentRiskRecalculationCoordinator(
      CreditEventCandidateRepository candidates, RiskCalculationRequestService risks) {
    this.candidates = candidates;
    this.risks = risks;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void approved(CandidateApprovedNotification e) {
    request(e.candidateId(), e.assetId(), e.reviewerUserId());
  }

  @Scheduled(fixedDelayString = "${app.documents.recalculation-retry-delay:60000}")
  public void retry() {
    for (CreditEventCandidate c :
        candidates.findByStatusAndRecalculationStatus(
            CreditEventCandidateStatus.APPROVED, RecalculationStatus.DEFERRED))
      request(c.getId(), c.getAssetId(), null);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void request(Long candidateId, Long assetId, Long user) {
    CreditEventCandidate c = candidates.findById(candidateId).orElseThrow();
    Long requester = user == null ? c.getReviewedByUserId() : user;
    if (requester == null) {
      c.recalculationDeferred();
      return;
    }
    try {
      var job = risks.request(requester, assetId);
      c.recalculationRequested(job.getJobId());
    } catch (BusinessException ex) {
      c.recalculationDeferred();
    }
  }
}
