package com.finrisk.radar.report.service;

import java.time.Duration;

/** Shared stale thresholds used by both recovery and the operations console. */
public final class ReportRecoveryPolicy {
  public static final Duration REQUESTED_STALE_AFTER = Duration.ofMinutes(1);
  public static final Duration RUNNING_STALE_AFTER = Duration.ofMinutes(5);

  private ReportRecoveryPolicy() {}
}
