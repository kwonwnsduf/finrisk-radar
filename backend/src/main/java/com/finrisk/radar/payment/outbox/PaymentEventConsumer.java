package com.finrisk.radar.payment.outbox;

import com.finrisk.radar.fsd.*;
import com.finrisk.radar.payment.PaymentFraudFactsService;
import java.util.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class PaymentEventConsumer {
  private static final String CONSUMER = "payment-fsd-v1";
  private final ConsumedEventRepository consumed;
  private final FsdEngine fsd;
  private final PaymentFraudFactsService facts;

  PaymentEventConsumer(
      ConsumedEventRepository consumed, FsdEngine fsd, PaymentFraudFactsService facts) {
    this.consumed = consumed;
    this.fsd = fsd;
    this.facts = facts;
  }

  @KafkaListener(
      topics = PaymentEventConfiguration.TOPIC,
      groupId = "finrisk-payment-fsd",
      containerFactory = "paymentKafkaListenerContainerFactory")
  @Transactional
  public void consume(PaymentDomainEvent event) {
    if (consumed.claim(CONSUMER, event.eventId()) == 0) return;
    Long orderId = longValue(event.payload().get("paymentOrderId"));
    Long userId = longValue(event.payload().get("userId"));
    if (orderId == null || userId == null) return;
    if ("PaymentCompletedEvent".equals(event.eventType())
        || "PaymentRecoveryCompletedEvent".equals(event.eventType())) {
      fsd.evaluatePost(
          FsdPhase.POST_PAYMENT,
          orderId,
          userId,
          facts.facts(FsdPhase.POST_PAYMENT, orderId, userId));
    } else if ("PaymentCanceledEvent".equals(event.eventType())) {
      fsd.evaluatePost(
          FsdPhase.POST_CANCEL,
          orderId,
          userId,
          facts.facts(FsdPhase.POST_CANCEL, orderId, userId));
    }
  }

  private Long longValue(Object value) {
    return value instanceof Number n ? n.longValue() : null;
  }
}
