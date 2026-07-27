package com.finrisk.radar.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
  Optional<PaymentTransaction> findByPaymentOrderId(Long orderId);

  Optional<PaymentTransaction> findByPaymentKey(String paymentKey);

  boolean existsByPaymentKeyAndPaymentOrderIdNot(String paymentKey, Long orderId);
}
