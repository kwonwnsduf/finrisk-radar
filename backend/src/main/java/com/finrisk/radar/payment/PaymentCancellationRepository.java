package com.finrisk.radar.payment;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentCancellationRepository extends JpaRepository<PaymentCancellation, Long> {
  Optional<PaymentCancellation> findByCancelRequestId(UUID requestId);

  Optional<PaymentCancellation> findByPaymentOrderId(Long orderId);
}
