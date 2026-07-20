package com.finrisk.radar.document.service;

import com.finrisk.radar.document.*;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.service.RiskCalculationJobService;
import com.finrisk.radar.risk.service.RiskCalculationRequestService;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DocumentRiskRecalculationCoordinator {
  private static final Logger log =
      LoggerFactory.getLogger(DocumentRiskRecalculationCoordinator.class);
  private final CreditEventCandidateRepository candidates;
  private final RiskCalculationRequestService risks;
  private final RiskCalculationJobService jobs;
  private final int maxAttempts;

  public DocumentRiskRecalculationCoordinator(
      CreditEventCandidateRepository candidates,
      RiskCalculationRequestService risks,
      RiskCalculationJobService jobs,
      @Value("${app.documents.recalculation-max-attempts:5}") int maxAttempts) {
    this.candidates = candidates;
    this.risks = risks;
    this.jobs = jobs;
    this.maxAttempts = maxAttempts;
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
    reconcileRequested();
  }

  public void request(Long candidateId, Long assetId, Long user) {
    CreditEventCandidate c = candidates.findById(candidateId).orElseThrow();
    if (c.getRecalculationStatus() == RecalculationStatus.REQUESTED
        || c.getRecalculationStatus() == RecalculationStatus.COMPLETED) return;
    Long requester = user == null ? c.getReviewedByUserId() : user;
    c.recalculationAttempted();
    if (requester == null) {
      c.recalculationDeferred("The approving user is unavailable.", maxAttempts);
      candidates.save(c);
      return;
    }
    try {
      var job = risks.request(requester, assetId);
      c.recalculationRequested(job.getJobId());
    } catch (BusinessException ex) {
      c.recalculationDeferred(ex.getMessage(), maxAttempts);
      log.warn(
          "Document-triggered risk recalculation deferred: candidateId={}, assetId={}, attempt={}",
          candidateId,
          assetId,
          c.getRecalculationAttemptCount(),
          ex);
    }
    candidates.save(c);
  }

  public void retryNow(Long candidateId, Long user) {
    CreditEventCandidate c = candidates.findById(candidateId).orElseThrow();
    if (c.getStatus() != CreditEventCandidateStatus.APPROVED)
      throw new IllegalStateException("Only approved candidates can be recalculated.");
    if (c.getRecalculationStatus() == RecalculationStatus.COMPLETED) return;
    if (c.getRecalculationStatus() == RecalculationStatus.REQUESTED
        && c.getRecalculationJobId() != null) {
      RiskCalculationJob current = jobs.get(c.getRecalculationJobId());
      if (current.getStatus() == RiskCalculationStatus.COMPLETED) {
        c.recalculationCompleted();
        candidates.save(c);
        return;
      }
      if (current.getStatus() != RiskCalculationStatus.FAILED) return;
    }
    c.resetRecalculationRetry();
    candidates.save(c);
    request(c.getId(), c.getAssetId(), user);
  }

  private void reconcileRequested() {
    for (CreditEventCandidate c :
        candidates.findByStatusAndRecalculationStatus(
            CreditEventCandidateStatus.APPROVED, RecalculationStatus.REQUESTED)) {
      if (c.getRecalculationJobId() == null) {
        c.recalculationDeferred("The recalculation job id is missing.", maxAttempts);
        candidates.save(c);
        continue;
      }
      try {
        RiskCalculationJob job = jobs.get(c.getRecalculationJobId());
        if (job.getStatus() == RiskCalculationStatus.COMPLETED) {
          c.recalculationCompleted();
          candidates.save(c);
        } else if (job.getStatus() == RiskCalculationStatus.FAILED) {
          c.recalculationFailed(job.getFailureMessage());
          candidates.save(c);
        }
      } catch (BusinessException ex) {
        c.recalculationDeferred(ex.getMessage(), maxAttempts);
        candidates.save(c);
        log.warn(
            "Document-triggered risk job reconciliation failed: candidateId={}, jobId={}",
            c.getId(),
            c.getRecalculationJobId(),
            ex);
      }
    }
  }
}
