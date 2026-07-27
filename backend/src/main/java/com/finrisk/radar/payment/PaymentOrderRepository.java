package com.finrisk.radar.payment;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
  Optional<PaymentOrder> findByOrderId(String orderId);

  Page<PaymentOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  long countByUserIdAndStatusAndCreatedAtAfter(
      Long userId, PaymentOrderStatus status, LocalDateTime after);

  List<PaymentOrder> findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
      Collection<PaymentOrderStatus> statuses, LocalDateTime before);

  List<PaymentOrder> findByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from PaymentOrder o where o.orderId = :orderId")
  Optional<PaymentOrder> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
