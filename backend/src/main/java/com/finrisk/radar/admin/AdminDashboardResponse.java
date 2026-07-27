package com.finrisk.radar.admin;

import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
    LocalDateTime asOf,
    String zoneId,
    LocalDateTime last24HoursFrom,
    LocalDateTime last7DaysFrom,
    Users users,
    Payments payments,
    Jobs jobs,
    Reviews reviews) {
  public record Users(
      long total,
      long free,
      long premium,
      long activeSubscriptions,
      long newLast24Hours,
      long newLast7Days,
      long newSubscriptionRecordsLast7Days) {}

  public record Money(String currency, long count, long amount) {}

  public record Payments(
      List<Money> approvedLast24Hours,
      List<Money> approvedLast7Days,
      long failedAttemptsLast24Hours,
      long failedAttemptsLast7Days,
      List<Money> canceledLast7Days,
      long recoveryRequired,
      long unresolvedFsd) {}

  public record Jobs(
      long activeBacktests,
      long failedBacktestsLast24Hours,
      long activeReports,
      long failedReportsLast24Hours,
      long staleReports,
      long failedMarketCollectionsLast24Hours,
      long failedDocumentCollectionsLast24Hours) {}

  public record Reviews(
      long openFsd,
      long reviewingFsd,
      long pendingCandidates,
      long pendingCandidateAssets,
      long newCandidatesLast24Hours) {}
}
