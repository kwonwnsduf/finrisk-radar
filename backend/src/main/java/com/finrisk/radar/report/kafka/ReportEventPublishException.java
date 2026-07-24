package com.finrisk.radar.report.kafka;

public class ReportEventPublishException extends RuntimeException {
  public ReportEventPublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
