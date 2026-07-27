package com.finrisk.radar.payment;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
  Optional<PaymentAttempt> findByUserIdAndAttemptTypeAndIdempotencyKey(
      Long userId, String attemptType, UUID idempotencyKey);

  long countByUserIdAndResultAndCreatedAtAfter(Long userId, String result, LocalDateTime after);

  @Query(
      "select count(distinct a.userId) from PaymentAttempt a where a.clientIp = :clientIp and"
          + " a.createdAt > :after")
  long countDistinctUsersByClientIp(
      @Param("clientIp") String clientIp, @Param("after") LocalDateTime after);

  Optional<PaymentAttempt> findTopByPaymentOrderIdOrderByCreatedAtDesc(Long paymentOrderId);

  @Query(
      "select count(a.id) from PaymentAttempt a "
          + "where a.clientIp = :clientIp and a.userId <> :userId "
          + "and a.result = 'FAILED' and a.createdAt > :after")
  long countOtherFailuresByClientIp(
      @Param("clientIp") String clientIp,
      @Param("userId") Long userId,
      @Param("after") LocalDateTime after);
}
