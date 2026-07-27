package com.finrisk.radar.payment;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PaymentOrderLookupService {
  private final PaymentOrderRepository orders;

  public PaymentOrderLookupService(PaymentOrderRepository orders) {
    this.orders = orders;
  }

  public Map<Long, String> publicOrderIds(Collection<Long> ids) {
    return orders.findAllById(ids).stream()
        .collect(Collectors.toMap(PaymentOrder::getId, PaymentOrder::getOrderId));
  }
}
