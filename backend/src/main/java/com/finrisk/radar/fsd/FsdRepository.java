package com.finrisk.radar.fsd;

import java.time.LocalDateTime;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface FsdRepository
    extends JpaRepository<FsdEvent, Long>, JpaSpecificationExecutor<FsdEvent> {
  long countByUserIdAndPhaseAndDetectedAtAfter(Long userId, FsdPhase phase, LocalDateTime after);

  boolean existsByPaymentOrderIdAndRuleCodeAndPhase(
      Long paymentOrderId, String ruleCode, FsdPhase phase);
}
