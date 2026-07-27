package com.finrisk.radar.admin;

import com.finrisk.radar.backtest.*;
import com.finrisk.radar.collector.log.*;
import com.finrisk.radar.document.*;
import com.finrisk.radar.fsd.*;
import com.finrisk.radar.payment.*;
import com.finrisk.radar.report.*;
import com.finrisk.radar.report.service.ReportRecoveryPolicy;
import com.finrisk.radar.subscription.*;
import com.finrisk.radar.user.UserRepository;
import java.time.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardQueryService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private final UserRepository users;
  private final SubscriptionRepository subscriptions;
  private final PaymentTransactionRepository transactions;
  private final PaymentAttemptRepository attempts;
  private final PaymentCancellationRepository cancellations;
  private final PaymentOrderRepository orders;
  private final FsdRepository fsd;
  private final BacktestJobRepository backtests;
  private final AiReportRepository reports;
  private final CollectionLogRepository collections;
  private final DocumentCollectionJobRepository documentCollections;
  private final CreditEventCandidateRepository candidates;

  public AdminDashboardQueryService(
      UserRepository users,
      SubscriptionRepository subscriptions,
      PaymentTransactionRepository transactions,
      PaymentAttemptRepository attempts,
      PaymentCancellationRepository cancellations,
      PaymentOrderRepository orders,
      FsdRepository fsd,
      BacktestJobRepository backtests,
      AiReportRepository reports,
      CollectionLogRepository collections,
      DocumentCollectionJobRepository documentCollections,
      CreditEventCandidateRepository candidates) {
    this.users = users;
    this.subscriptions = subscriptions;
    this.transactions = transactions;
    this.attempts = attempts;
    this.cancellations = cancellations;
    this.orders = orders;
    this.fsd = fsd;
    this.backtests = backtests;
    this.reports = reports;
    this.collections = collections;
    this.documentCollections = documentCollections;
    this.candidates = candidates;
  }

  @Transactional(readOnly = true)
  public AdminDashboardResponse get() {
    LocalDateTime now = LocalDateTime.now(ZONE);
    LocalDateTime day = now.minusHours(24);
    LocalDateTime week = now.minusDays(7);
    long open = fsd.countByStatus(FsdStatus.OPEN);
    long reviewing = fsd.countByStatus(FsdStatus.REVIEWING);
    return new AdminDashboardResponse(
        now,
        ZONE.getId(),
        day,
        week,
        new AdminDashboardResponse.Users(
            users.count(),
            users.countByPlan(PlanType.FREE),
            users.countByPlan(PlanType.PREMIUM),
            subscriptions.countByStatusAndCurrentPeriodEndAfter(SubscriptionStatus.ACTIVE, now),
            users.countByCreatedAtAfter(day),
            users.countByCreatedAtAfter(week),
            subscriptions.countByCreatedAtAfter(week)),
        new AdminDashboardResponse.Payments(
            money(transactions.summarizeApprovedAfter(day)),
            money(transactions.summarizeApprovedAfter(week)),
            attempts.countByResultAndCompletedAtAfter("FAILED", day),
            attempts.countByResultAndCompletedAtAfter("FAILED", week),
            money(cancellations.summarizeCompletedAfter(week)),
            orders.countByStatus(PaymentOrderStatus.RECOVERY_REQUIRED),
            open + reviewing),
        new AdminDashboardResponse.Jobs(
            backtests.countByStatusIn(List.of(BacktestStatus.REQUESTED, BacktestStatus.RUNNING)),
            backtests.countByStatusAndCompletedAtAfter(BacktestStatus.FAILED, day),
            reports.countByStatusIn(List.of(ReportStatus.REQUESTED, ReportStatus.RUNNING)),
            reports.countByStatusAndFailedAtAfter(ReportStatus.FAILED, day),
            reports.countByStatusAndRequestedAtBefore(
                    ReportStatus.REQUESTED, now.minus(ReportRecoveryPolicy.REQUESTED_STALE_AFTER))
                + reports.countByStatusAndStartedAtBefore(
                    ReportStatus.RUNNING, now.minus(ReportRecoveryPolicy.RUNNING_STALE_AFTER)),
            collections.countByStatusAndCompletedAtAfter(CollectionStatus.FAILED, day),
            documentCollections.countByStatusAndCompletedAtAfter(
                DocumentCollectionStatus.FAILED, day)),
        new AdminDashboardResponse.Reviews(
            open,
            reviewing,
            candidates.countByStatus(CreditEventCandidateStatus.PENDING_REVIEW),
            candidates.countDistinctAssetsByStatus(CreditEventCandidateStatus.PENDING_REVIEW),
            candidates.countByStatusAndCreatedAtAfter(
                CreditEventCandidateStatus.PENDING_REVIEW, day)));
  }

  private static List<AdminDashboardResponse.Money> money(List<PaymentMoneySummary> values) {
    return values.stream()
        .map(v -> new AdminDashboardResponse.Money(v.currency(), v.count(), v.amount()))
        .toList();
  }
}
