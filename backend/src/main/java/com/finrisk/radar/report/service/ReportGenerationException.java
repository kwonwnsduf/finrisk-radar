package com.finrisk.radar.report.service;

public class ReportGenerationException extends RuntimeException {
  private final String code;
  private final boolean retryable;

  public ReportGenerationException(
      String code, String message, boolean retryable, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.retryable = retryable;
  }

  public String getCode() {
    return code;
  }

  public boolean isRetryable() {
    return retryable;
  }
}
