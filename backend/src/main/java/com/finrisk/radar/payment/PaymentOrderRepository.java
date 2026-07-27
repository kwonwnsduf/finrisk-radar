package com.finrisk.radar.payment;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository
    extends JpaRepository<PaymentOrder, Long>, JpaSpecificationExecutor<PaymentOrder> {
  Optional<PaymentOrder> findByOrderId(String orderId);

  Page<PaymentOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  List<PaymentOrder> findByIdIn(Collection<Long> ids);

  @Query("""
      select o from PaymentOrder o
      where o.userId in :userIds
        and o.createdAt = (
          select max(o2.createdAt) from PaymentOrder o2 where o2.userId = o.userId
        )
      """)
  List<PaymentOrder> findLatestByUserIds(@Param("userIds") Collection<Long> userIds);

  long countByStatus(PaymentOrderStatus status);

  long countByUserIdAndStatusAndCreatedAtAfter(
      Long userId, PaymentOrderStatus status, LocalDateTime after);

  List<PaymentOrder> findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
      Collection<PaymentOrderStatus> statuses, LocalDateTime before);

  List<PaymentOrder> findByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from PaymentOrder o where o.orderId = :orderId")
  Optional<PaymentOrder> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
