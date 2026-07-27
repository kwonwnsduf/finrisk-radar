package com.finrisk.radar.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.finrisk.radar.document.*;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.api.CreditEventCreateRequest;
import com.finrisk.radar.risk.service.RiskAdminService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CreditEventReviewServiceTest {

  @Test
  void approvalLocksCandidateCreatesCreditEventAndPublishesNotification() {
    CreditEventCandidateRepository candidates = mock(CreditEventCandidateRepository.class);
    DocumentRiskMatchRepository matches = mock(DocumentRiskMatchRepository.class);
    RiskAdminService risks = mock(RiskAdminService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    CreditEventCandidate candidate = pending();
    when(candidates.findByIdForUpdate(1L)).thenReturn(Optional.of(candidate));
    when(matches.findByCandidateIdOrderByConfidenceDesc(1L)).thenReturn(List.of());
    when(risks.createEvent(eq(10L), any(CreditEventCreateRequest.class)))
        .thenReturn(
            CreditEvent.create(
                10L,
                CreditEventType.REFINANCING_FAILURE,
                LocalDate.now(),
                RiskSeverity.HIGH,
                "DOCUMENT",
                "test",
                null,
                "test",
                "incident",
                "candidate-1"));
    CreditEventReviewService service =
        new CreditEventReviewService(candidates, matches, risks, events);

    service.approve(1L, 42L, "confirmed");

    assertThat(candidate.getStatus()).isEqualTo(CreditEventCandidateStatus.APPROVED);
    assertThat(candidate.getReviewedByUserId()).isEqualTo(42L);
    verify(candidates).findByIdForUpdate(1L);
    verify(events).publishEvent(any(CandidateApprovedNotification.class));
  }

  @Test
  void reviewedCandidateCannotBeProcessedAgain() {
    CreditEventCandidateRepository candidates = mock(CreditEventCandidateRepository.class);
    DocumentRiskMatchRepository matches = mock(DocumentRiskMatchRepository.class);
    RiskAdminService risks = mock(RiskAdminService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    CreditEventCandidate candidate = pending();
    candidate.reject(7L, "not an event");
    when(candidates.findByIdForUpdate(1L)).thenReturn(Optional.of(candidate));
    CreditEventReviewService service =
        new CreditEventReviewService(candidates, matches, risks, events);

    assertThatThrownBy(() -> service.approve(1L, 42L, "retry"))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(risks, events);
  }

  private static CreditEventCandidate pending() {
    return CreditEventCandidate.pending(
        10L,
        CreditEventType.REFINANCING_FAILURE,
        LocalDate.now(),
        RiskSeverity.HIGH,
        new BigDecimal("0.9"),
        "incident");
  }
}
