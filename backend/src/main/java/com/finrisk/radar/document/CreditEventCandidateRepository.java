package com.finrisk.radar.document;

import com.finrisk.radar.risk.CreditEventType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface CreditEventCandidateRepository
    extends JpaRepository<CreditEventCandidate, Long>,
        JpaSpecificationExecutor<CreditEventCandidate> {
  List<CreditEventCandidate> findByStatusOrderByConfidenceDescEventDateDesc(
      CreditEventCandidateStatus status);

  List<CreditEventCandidate> findByStatusAndRecalculationStatus(
      CreditEventCandidateStatus status, RecalculationStatus recalculationStatus);

  List<CreditEventCandidate> findByAssetIdAndEventTypeAndEventDateBetween(
      Long asset, CreditEventType type, LocalDate from, LocalDate to);

  long countByStatus(CreditEventCandidateStatus status);

  long countByStatusAndCreatedAtAfter(
      CreditEventCandidateStatus status, LocalDateTime after);

  @Query("select count(distinct c.assetId) from CreditEventCandidate c where c.status = :status")
  long countDistinctAssetsByStatus(@Param("status") CreditEventCandidateStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from CreditEventCandidate c where c.id = :id")
  Optional<CreditEventCandidate> findByIdForUpdate(@Param("id") Long id);
}
