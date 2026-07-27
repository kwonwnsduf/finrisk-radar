package com.finrisk.radar.subscription;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SubscriptionEntitlementRepository
    extends JpaRepository<SubscriptionEntitlement, Long> {
  Optional<SubscriptionEntitlement> findByPaymentOrderId(Long orderId);

  List<SubscriptionEntitlement> findByUserIdOrderByPeriodStartAscIdAsc(Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select e from SubscriptionEntitlement e where e.userId = :userId order by e.periodStart asc,"
          + " e.id asc")
  List<SubscriptionEntitlement> findByUserIdForUpdate(@Param("userId") Long userId);
}
