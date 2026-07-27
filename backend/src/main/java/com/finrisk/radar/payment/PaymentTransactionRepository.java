package com.finrisk.radar.payment;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
  Optional<PaymentTransaction> findByPaymentOrderId(Long orderId);

  Optional<PaymentTransaction> findByPaymentKey(String paymentKey);

  boolean existsByPaymentKeyAndPaymentOrderIdNot(String paymentKey, Long orderId);

  @Query("""
      select new com.finrisk.radar.payment.PaymentMoneySummary(
        o.currency, count(t.id), coalesce(sum(t.totalAmount), 0))
      from PaymentTransaction t, PaymentOrder o
      where o.id = t.paymentOrderId and t.approvedAt >= :after
      group by o.currency
      """)
  List<PaymentMoneySummary> summarizeApprovedAfter(LocalDateTime after);
}
