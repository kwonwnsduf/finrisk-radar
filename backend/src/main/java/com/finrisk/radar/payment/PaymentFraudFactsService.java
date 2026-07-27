package com.finrisk.radar.payment;

import com.finrisk.radar.fsd.FsdPhase;
import com.finrisk.radar.fsd.FsdProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentFraudFactsService {
  private final PaymentOrderRepository orders;
  private final PaymentAttemptRepository attempts;
  private final FsdProperties properties;

  public PaymentFraudFactsService(
      PaymentOrderRepository orders, PaymentAttemptRepository attempts, FsdProperties properties) {
    this.orders = orders;
    this.attempts = attempts;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> facts(FsdPhase phase, Long orderId, Long userId) {
    LocalDateTime now = LocalDateTime.now();
    Map<String, Object> facts = new LinkedHashMap<>();
    if (phase == FsdPhase.POST_PAYMENT) {
      List<PaymentOrder> recent =
          orders.findByUserIdAndCreatedAtAfter(
              userId, now.minus(properties.repeatedOrderPattern().window()));
      long paid = recent.stream().filter(this::hasSuccessfulPayment).count();
      facts.put("recentOrders", recent.size());
      facts.put("paidRatio", ratio(paid, recent.size()));
      addIpFacts(facts, orderId, userId, now);
    } else if (phase == FsdPhase.POST_CANCEL) {
      List<PaymentOrder> recent =
          orders.findByUserIdAndCreatedAtAfter(
              userId, now.minus(properties.cancellationRatio().lookbackWindow()));
      long payments = recent.stream().filter(this::hasSuccessfulPayment).count();
      long canceled =
          recent.stream().filter(o -> o.getStatus() == PaymentOrderStatus.CANCELED).count();
      long immediate =
          recent.stream()
              .filter(o -> o.getStatus() == PaymentOrderStatus.CANCELED)
              .filter(
                  o ->
                      o.getPaidAt() != null
                          && o.getCanceledAt() != null
                          && Duration.between(o.getPaidAt(), o.getCanceledAt())
                                  .compareTo(properties.immediateCancel().immediateWindow())
                              <= 0)
              .count();
      facts.put("payments", payments);
      facts.put("cancelRatio", ratio(canceled, payments));
      facts.put("immediateCancels", immediate);
      addIpFacts(facts, orderId, userId, now);
    }
    return facts;
  }

  private void addIpFacts(Map<String, Object> facts, Long orderId, Long userId, LocalDateTime now) {
    attempts
        .findTopByPaymentOrderIdOrderByCreatedAtDesc(orderId)
        .map(PaymentAttempt::getClientIp)
        .filter(ip -> !ip.isBlank())
        .ifPresent(
            ip -> {
              facts.put(
                  "sharedIpAccounts",
                  attempts.countDistinctUsersByClientIp(
                      ip, now.minus(properties.sameIpAccounts().window())));
              facts.put(
                  "failureSwitchAccounts",
                  attempts.countOtherFailuresByClientIp(
                      ip, userId, now.minus(properties.failureSwitchAccount().failureWindow())));
            });
  }

  private boolean hasSuccessfulPayment(PaymentOrder order) {
    return order.getPaidAt() != null;
  }

  private double ratio(long numerator, long denominator) {
    return denominator == 0 ? 0 : (double) numerator / denominator;
  }
}
