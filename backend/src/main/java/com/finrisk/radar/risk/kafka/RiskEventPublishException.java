package com.finrisk.radar.risk.kafka;

public class RiskEventPublishException extends RuntimeException {
  public RiskEventPublishException(String m, Throwable c) {
    super(m, c);
  }
}
