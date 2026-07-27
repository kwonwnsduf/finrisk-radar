package com.finrisk.radar.fsd;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface FsdRepository
    extends JpaRepository<FsdEvent, Long>, JpaSpecificationExecutor<FsdEvent> {
  long countByUserIdAndPhaseAndDetectedAtAfter(Long userId, FsdPhase phase, LocalDateTime after);

  boolean existsByPaymentOrderIdAndRuleCodeAndPhase(
      Long paymentOrderId, String ruleCode, FsdPhase phase);

  long countByStatus(FsdStatus status);

  long countByStatusIn(Collection<FsdStatus> statuses);

  List<FsdEvent> findByPaymentOrderIdIn(Collection<Long> paymentOrderIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from FsdEvent e where e.id = :id")
  Optional<FsdEvent> findByIdForUpdate(@Param("id") Long id);
}
