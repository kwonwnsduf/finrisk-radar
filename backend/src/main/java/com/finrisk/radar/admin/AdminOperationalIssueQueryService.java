package com.finrisk.radar.admin;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.backtest.*;
import com.finrisk.radar.collector.log.*;
import com.finrisk.radar.document.*;
import com.finrisk.radar.report.*;
import com.finrisk.radar.report.service.ReportRecoveryPolicy;
import com.finrisk.radar.user.*;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperationalIssueQueryService {
  private final BacktestJobRepository backtests;
  private final AiReportRepository reports;
  private final CollectionLogRepository marketCollections;
  private final DocumentCollectionJobRepository documentCollections;
  private final UserRepository users;
  private final AssetRepository assets;

  public AdminOperationalIssueQueryService(
      BacktestJobRepository backtests,
      AiReportRepository reports,
      CollectionLogRepository marketCollections,
      DocumentCollectionJobRepository documentCollections,
      UserRepository users,
      AssetRepository assets) {
    this.backtests = backtests;
    this.reports = reports;
    this.marketCollections = marketCollections;
    this.documentCollections = documentCollections;
    this.users = users;
    this.assets = assets;
  }

  @Transactional(readOnly = true)
  public AdminPage<AdminOperationalIssue> backtests(int page, int size) {
    Page<BacktestJob> result =
        backtests.findByStatusOrderByCompletedAtDesc(
            BacktestStatus.FAILED, request(page, size));
    return AdminPage.from(
        result,
        enrich(
            result.stream()
                .map(
                    value ->
                        raw(
                            "BACKTEST_FAILED",
                            value.getJobId().toString(),
                            value.getRequestedByUserId(),
                            value.getAssetId(),
                            value.getStatus().name(),
                            value.getCreatedAt(),
                            value.getStartedAt(),
                            value.getCompletedAt(),
                            value.getUpdatedAt(),
                            null,
                            value.getMessage()))
                .toList()));
  }

  @Transactional(readOnly = true)
  public AdminPage<AdminOperationalIssue> reports(int page, int size) {
    LocalDateTime now = LocalDateTime.now();
    Specification<AiReport> specification =
        (root, query, cb) -> {
          Predicate failed = cb.equal(root.get("status"), ReportStatus.FAILED);
          Predicate staleRequested =
              cb.and(
                  cb.equal(root.get("status"), ReportStatus.REQUESTED),
                  cb.lessThan(
                      root.get("requestedAt"),
                      now.minus(ReportRecoveryPolicy.REQUESTED_STALE_AFTER)));
          Predicate staleRunning =
              cb.and(
                  cb.equal(root.get("status"), ReportStatus.RUNNING),
                  cb.lessThan(
                      root.get("startedAt"),
                      now.minus(ReportRecoveryPolicy.RUNNING_STALE_AFTER)));
          return cb.or(failed, staleRequested, staleRunning);
        };
    Page<AiReport> result =
        reports.findAll(
            specification,
            PageRequest.of(
                Math.max(0, page),
                Math.min(100, Math.max(1, size)),
                Sort.by("requestedAt").descending()));
    return AdminPage.from(
        result,
        enrich(
            result.stream()
                .map(
                    value ->
                        raw(
                            value.getStatus() == ReportStatus.FAILED
                                ? "REPORT_FAILED"
                                : "REPORT_STALE_" + value.getStatus(),
                            value.getId().toString(),
                            value.getUserId(),
                            value.getAssetId(),
                            value.getStatus().name(),
                            value.getRequestedAt(),
                            value.getStartedAt(),
                            value.getCompletedAt(),
                            value.getUpdatedAt(),
                            value.getFailureCode(),
                            value.getFailureMessage()))
                .toList()));
  }

  @Transactional(readOnly = true)
  public AdminPage<AdminOperationalIssue> collections(
      CollectionIssueKind kind, int page, int size) {
    if (kind == CollectionIssueKind.DOCUMENT) {
      Page<DocumentCollectionJob> result =
          documentCollections.findByStatusOrderByCompletedAtDesc(
              DocumentCollectionStatus.FAILED, request(page, size));
      return AdminPage.from(
          result,
          enrich(
              result.stream()
                  .map(
                      value ->
                          raw(
                              "DOCUMENT_COLLECTION_FAILED",
                              value.getJobId().toString(),
                              value.getRequestedByUserId(),
                              value.getAssetId(),
                              value.getStatus().name(),
                              value.getRequestedAt(),
                              value.getStartedAt(),
                              value.getCompletedAt(),
                              value.getUpdatedAt(),
                              value.getFailureCode(),
                              value.getFailureMessage()))
                  .toList()));
    }
    Page<CollectionLog> result =
        marketCollections.findByStatusOrderByCompletedAtDesc(
            CollectionStatus.FAILED, request(page, size));
    return AdminPage.from(
        result,
        enrich(
            result.stream()
                .map(
                    value ->
                        raw(
                            "MARKET_COLLECTION_FAILED",
                            value.getJobId().toString(),
                            value.getRequestedByUserId(),
                            value.getAssetId(),
                            value.getStatus().name(),
                            value.getCreatedAt(),
                            value.getStartedAt(),
                            value.getCompletedAt(),
                            value.getUpdatedAt(),
                            null,
                            value.getMessage()))
                .toList()));
  }

  private List<AdminOperationalIssue> enrich(List<Raw> values) {
    Map<Long, User> userById =
        users.findAllById(values.stream().map(Raw::userId).filter(Objects::nonNull).toList()).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    Map<Long, Asset> assetById =
        assets.findAllById(values.stream().map(Raw::assetId).filter(Objects::nonNull).toList()).stream()
            .collect(Collectors.toMap(Asset::getId, Function.identity()));
    LocalDateTime now = LocalDateTime.now();
    return values.stream()
        .map(
            value -> {
              User user = userById.get(value.userId());
              Asset asset = assetById.get(value.assetId());
              LocalDateTime basis =
                  value.completedAt() != null
                      ? value.completedAt()
                      : value.startedAt() != null ? value.startedAt() : value.requestedAt();
              return new AdminOperationalIssue(
                  value.issueType(),
                  value.jobId(),
                  value.userId(),
                  user == null ? null : user.getEmail(),
                  value.assetId(),
                  asset == null ? null : asset.getName(),
                  asset == null ? null : asset.getTicker(),
                  value.status(),
                  value.requestedAt(),
                  value.startedAt(),
                  value.completedAt(),
                  value.updatedAt(),
                  value.failureCode(),
                  value.failureMessage(),
                  basis == null ? 0 : Math.max(0, Duration.between(basis, now).getSeconds()));
            })
        .toList();
  }

  private static PageRequest request(int page, int size) {
    return PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
  }

  private static Raw raw(
      String issueType,
      String jobId,
      Long userId,
      Long assetId,
      String status,
      LocalDateTime requestedAt,
      LocalDateTime startedAt,
      LocalDateTime completedAt,
      LocalDateTime updatedAt,
      String failureCode,
      String failureMessage) {
    return new Raw(
        issueType,
        jobId,
        userId,
        assetId,
        status,
        requestedAt,
        startedAt,
        completedAt,
        updatedAt,
        failureCode,
        failureMessage);
  }

  private record Raw(
      String issueType,
      String jobId,
      Long userId,
      Long assetId,
      String status,
      LocalDateTime requestedAt,
      LocalDateTime startedAt,
      LocalDateTime completedAt,
      LocalDateTime updatedAt,
      String failureCode,
      String failureMessage) {}
}
