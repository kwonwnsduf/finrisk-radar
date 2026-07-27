package com.finrisk.radar.subscription;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
  Optional<Subscription> findByUserId(Long userId);

  List<Subscription> findTop100ByStatusAndCurrentPeriodEndBeforeOrderByCurrentPeriodEndAsc(
      SubscriptionStatus status, LocalDateTime now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Subscription s where s.userId = :userId")
  Optional<Subscription> findByUserIdForUpdate(@Param("userId") Long userId);
}
