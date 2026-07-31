package com.finrisk.radar.report.kafka;

public final class ReportTopics {
  public static final String GENERATION_REQUESTED = "report-generation-requested";
  public static final String COMPLETED = "report-completed";
  public static final String FAILED = "report-failed";

  private ReportTopics() {}
}
