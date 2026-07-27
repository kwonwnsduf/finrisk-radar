package com.finrisk.radar.payment;

public enum PaymentOrderStatus {
  READY,
  CONFIRMING,
  PAID,
  CANCELING,
  CANCELED,
  FAILED,
  RECOVERY_REQUIRED
}
