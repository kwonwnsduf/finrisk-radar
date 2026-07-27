package com.finrisk.radar.payment;

public enum PaymentProduct {
  PREMIUM_MONTHLY("PREMIUM_MONTHLY", "FinRisk Radar PREMIUM", 5900L);

  private final String code;
  private final String orderName;
  private final long amount;

  PaymentProduct(String code, String orderName, long amount) {
    this.code = code;
    this.orderName = orderName;
    this.amount = amount;
  }

  public String code() {
    return code;
  }

  public String orderName() {
    return orderName;
  }

  public long amount() {
    return amount;
  }

  public static PaymentProduct require(String code) {
    if (PREMIUM_MONTHLY.code.equals(code)) return PREMIUM_MONTHLY;
    throw new IllegalArgumentException("Unsupported product code.");
  }
}
