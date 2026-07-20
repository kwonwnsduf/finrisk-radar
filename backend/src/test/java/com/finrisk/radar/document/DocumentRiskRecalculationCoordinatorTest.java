package com.finrisk.radar.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.finrisk.radar.risk.RiskCalculationJob;
import com.finrisk.radar.risk.service.*;
import com.finrisk.radar.document.service.DocumentRiskRecalculationCoordinator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentRiskRecalculationCoordinatorTest {
  @Test
  void persistsTheRequestedJobAfterApproval() {
    CreditEventCandidateRepository candidates = mock(CreditEventCandidateRepository.class);
    RiskCalculationRequestService risks = mock(RiskCalculationRequestService.class);
    RiskCalculationJobService jobs = mock(RiskCalculationJobService.class);
    CreditEventCandidate candidate = approvedCandidate();
    RiskCalculationJob job =
        RiskCalculationJob.requested(7L, 10L, "corporate-risk-v1", LocalDate.now());
    when(candidates.findById(1L)).thenReturn(Optional.of(candidate));
    when(risks.request(7L, 10L)).thenReturn(job);
    DocumentRiskRecalculationCoordinator coordinator =
        new DocumentRiskRecalculationCoordinator(candidates, risks, jobs, 5);

    coordinator.request(1L, 10L, 7L);

    assertEquals(RecalculationStatus.REQUESTED, candidate.getRecalculationStatus());
    assertEquals(job.getJobId(), candidate.getRecalculationJobId());
    assertEquals(1, candidate.getRecalculationAttemptCount());
    verify(candidates).save(candidate);
  }

  @Test
  void reconcilesACompletedRiskJob() {
    CreditEventCandidateRepository candidates = mock(CreditEventCandidateRepository.class);
    RiskCalculationRequestService risks = mock(RiskCalculationRequestService.class);
    RiskCalculationJobService jobs = mock(RiskCalculationJobService.class);
    CreditEventCandidate candidate = approvedCandidate();
    RiskCalculationJob job =
        RiskCalculationJob.requested(7L, 10L, "corporate-risk-v1", LocalDate.now());
    candidate.recalculationRequested(job.getJobId());
    job.complete();
    when(candidates.findByStatusAndRecalculationStatus(
            CreditEventCandidateStatus.APPROVED, RecalculationStatus.DEFERRED))
        .thenReturn(java.util.List.of());
    when(candidates.findByStatusAndRecalculationStatus(
            CreditEventCandidateStatus.APPROVED, RecalculationStatus.REQUESTED))
        .thenReturn(java.util.List.of(candidate));
    when(jobs.get(job.getJobId())).thenReturn(job);
    DocumentRiskRecalculationCoordinator coordinator =
        new DocumentRiskRecalculationCoordinator(candidates, risks, jobs, 5);

    coordinator.retry();

    assertEquals(RecalculationStatus.COMPLETED, candidate.getRecalculationStatus());
    verify(candidates).save(candidate);
  }

  private CreditEventCandidate approvedCandidate() {
    CreditEventCandidate candidate =
        CreditEventCandidate.pending(
            10L,
            com.finrisk.radar.risk.CreditEventType.REFINANCING_FAILURE,
            LocalDate.now(),
            com.finrisk.radar.risk.RiskSeverity.HIGH,
            new BigDecimal("0.9"),
            "incident");
    candidate.approve(7L, "approved", 99L);
    return candidate;
  }
}
