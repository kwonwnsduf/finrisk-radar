package com.finrisk.radar.payment;

import java.util.*;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentCancellationRepository extends JpaRepository<PaymentCancellation, Long> {
  Optional<PaymentCancellation> findByCancelRequestId(UUID requestId);

  Optional<PaymentCancellation> findByPaymentOrderId(Long orderId);

  List<PaymentCancellation> findByPaymentOrderIdIn(Collection<Long> orderIds);

  @Query("""
      select new com.finrisk.radar.payment.PaymentMoneySummary(
        o.currency, count(c.id), coalesce(sum(c.amount), 0))
      from PaymentCancellation c, PaymentOrder o
      where o.id = c.paymentOrderId
        and c.status = 'COMPLETED'
        and c.completedAt >= :after
      group by o.currency
      """)
  List<PaymentMoneySummary> summarizeCompletedAfter(LocalDateTime after);
}
