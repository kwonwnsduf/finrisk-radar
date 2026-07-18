package com.finrisk.radar.document;

import com.finrisk.radar.risk.CreditEventType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditEventCandidateRepository extends JpaRepository<CreditEventCandidate, Long> {
  List<CreditEventCandidate> findByStatusOrderByConfidenceDescEventDateDesc(
      CreditEventCandidateStatus status);

  List<CreditEventCandidate> findByStatusAndRecalculationStatus(
      CreditEventCandidateStatus status, RecalculationStatus recalculationStatus);

  List<CreditEventCandidate> findByAssetIdAndEventTypeAndEventDateBetween(
      Long asset, CreditEventType type, LocalDate from, LocalDate to);
}
