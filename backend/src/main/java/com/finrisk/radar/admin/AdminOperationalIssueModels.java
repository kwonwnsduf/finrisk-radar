package com.finrisk.radar.admin;

import java.time.LocalDateTime;

enum CollectionIssueKind {
  MARKET_DATA,
  DOCUMENT
}

record AdminOperationalIssue(
    String issueType,
    String jobId,
    Long userId,
    String email,
    Long assetId,
    String assetName,
    String ticker,
    String status,
    LocalDateTime requestedAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime updatedAt,
    String failureCode,
    String failureMessage,
    long ageSeconds) {}
