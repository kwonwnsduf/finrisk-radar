package com.finrisk.radar.payment;

import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class PaymentRecoveryScheduler {
  private static final Logger log = LoggerFactory.getLogger(PaymentRecoveryScheduler.class);
  private final PaymentOrderRepository orders;
  private final PaymentService payments;
  private final PaymentProperties properties;

  PaymentRecoveryScheduler(
      PaymentOrderRepository orders, PaymentService payments, PaymentProperties properties) {
    this.orders = orders;
    this.payments = payments;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${app.payment.recovery.fixed-delay:60s}")
  public void recover() {
    var statuses =
        List.of(
            PaymentOrderStatus.CONFIRMING,
            PaymentOrderStatus.CANCELING,
            PaymentOrderStatus.RECOVERY_REQUIRED);
    for (PaymentOrder order :
        orders.findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            statuses, LocalDateTime.now().minus(properties.recovery().staleAfter()))) {
      try {
        payments.reconcile(order.getOrderId());
      } catch (RuntimeException exception) {
        log.warn("Payment reconciliation failed for orderId={}", order.getOrderId());
      }
    }
  }
}
