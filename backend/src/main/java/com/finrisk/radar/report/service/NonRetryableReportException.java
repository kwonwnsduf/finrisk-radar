package com.finrisk.radar.report.service;

public class NonRetryableReportException extends ReportGenerationException {
  public NonRetryableReportException(String code, String message, Throwable cause) {
    super(code, message, false, cause);
  }
}
