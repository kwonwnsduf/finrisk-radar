package com.finrisk.radar.document;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "document_collection_jobs")
public class DocumentCollectionJob extends BaseTimeEntity {
  @Id
  @Column(name = "job_id")
  private UUID jobId;

  @Column(name = "requested_by_user_id")
  private Long requestedByUserId;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false)
  private DocumentSourceType sourceType;

  @Column(name = "from_date", nullable = false)
  private LocalDate fromDate;

  @Column(name = "to_date", nullable = false)
  private LocalDate toDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentCollectionStatus status;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "document_count", nullable = false)
  private int documentCount;

  @Column(name = "failure_code")
  private String failureCode;

  @Column(name = "failure_message", length = 1000)
  private String failureMessage;

  protected DocumentCollectionJob() {}

  public static DocumentCollectionJob requested(
      Long user, Long asset, DocumentSourceType source, LocalDate from, LocalDate to) {
    DocumentCollectionJob j = new DocumentCollectionJob();
    j.jobId = UUID.randomUUID();
    j.requestedByUserId = user;
    j.assetId = asset;
    j.sourceType = source;
    j.fromDate = from;
    j.toDate = to;
    j.status = DocumentCollectionStatus.REQUESTED;
    j.requestedAt = LocalDateTime.now();
    return j;
  }

  public boolean start() {
    if (status != DocumentCollectionStatus.REQUESTED) return false;
    status = DocumentCollectionStatus.RUNNING;
    startedAt = LocalDateTime.now();
    return true;
  }

  public void complete(int count) {
    status = DocumentCollectionStatus.COMPLETED;
    documentCount = count;
    completedAt = LocalDateTime.now();
  }

  public void fail(String code, String message) {
    if (status == DocumentCollectionStatus.COMPLETED) return;
    status = DocumentCollectionStatus.FAILED;
    failureCode = code;
    failureMessage = message;
    completedAt = LocalDateTime.now();
  }

  public UUID getJobId() {
    return jobId;
  }

  public Long getRequestedByUserId() {
    return requestedByUserId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public DocumentSourceType getSourceType() {
    return sourceType;
  }

  public LocalDate getFromDate() {
    return fromDate;
  }

  public LocalDate getToDate() {
    return toDate;
  }

  public DocumentCollectionStatus getStatus() {
    return status;
  }

  public int getDocumentCount() {
    return documentCount;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public String getFailureMessage() {
    return failureMessage;
  }
}
