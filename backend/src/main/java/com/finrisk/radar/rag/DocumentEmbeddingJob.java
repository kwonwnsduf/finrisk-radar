package com.finrisk.radar.rag;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_embedding_jobs")
public class DocumentEmbeddingJob extends BaseTimeEntity {
  @Id
  @Column(name = "job_id")
  private UUID jobId;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(name = "content_version", nullable = false)
  private int contentVersion;

  @Column(name = "source_content_hash", nullable = false, length = 64)
  private String sourceContentHash;

  @Column(name = "embedding_model", nullable = false, length = 100)
  private String embeddingModel;

  @Column(name = "embedding_dimensions", nullable = false)
  private int embeddingDimensions;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EmbeddingJobStatus status;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "chunk_count", nullable = false)
  private int chunkCount;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "failed_at")
  private LocalDateTime failedAt;

  @Column(name = "failure_code", length = 100)
  private String failureCode;

  @Column(name = "failure_message", length = 1000)
  private String failureMessage;

  protected DocumentEmbeddingJob() {}

  public static DocumentEmbeddingJob requested(
      Long documentId, int contentVersion, String hash, String model, int dimensions) {
    DocumentEmbeddingJob job = new DocumentEmbeddingJob();
    job.jobId = UUID.randomUUID();
    job.documentId = documentId;
    job.contentVersion = contentVersion;
    job.sourceContentHash = hash;
    job.embeddingModel = model;
    job.embeddingDimensions = dimensions;
    job.status = EmbeddingJobStatus.REQUESTED;
    job.requestedAt = LocalDateTime.now();
    return job;
  }

  public static DocumentEmbeddingJob skipped(
      Long documentId, int contentVersion, String hash, String model, int dimensions) {
    DocumentEmbeddingJob job = requested(documentId, contentVersion, hash, model, dimensions);
    job.status = EmbeddingJobStatus.SKIPPED;
    job.completedAt = LocalDateTime.now();
    return job;
  }

  public boolean terminal() {
    return status == EmbeddingJobStatus.COMPLETED || status == EmbeddingJobStatus.SKIPPED;
  }

  public void start() {
    if (terminal()) return;
    status = EmbeddingJobStatus.PROCESSING;
    startedAt = LocalDateTime.now();
    attemptCount++;
    failureCode = null;
    failureMessage = null;
    failedAt = null;
  }

  public void requestedAgain() {
    if (status == EmbeddingJobStatus.FAILED) {
      status = EmbeddingJobStatus.REQUESTED;
      requestedAt = LocalDateTime.now();
      failureCode = null;
      failureMessage = null;
      failedAt = null;
    }
  }

  public void complete(int chunks) {
    status = EmbeddingJobStatus.COMPLETED;
    active = true;
    chunkCount = chunks;
    completedAt = LocalDateTime.now();
  }

  public void fail(String code, String message) {
    if (terminal()) return;
    status = EmbeddingJobStatus.FAILED;
    active = false;
    failedAt = LocalDateTime.now();
    failureCode = code;
    failureMessage = truncate(message);
  }

  private String truncate(String message) {
    return message == null ? null : message.substring(0, Math.min(1000, message.length()));
  }

  public UUID getJobId() {
    return jobId;
  }

  public Long getDocumentId() {
    return documentId;
  }

  public int getContentVersion() {
    return contentVersion;
  }

  public String getSourceContentHash() {
    return sourceContentHash;
  }

  public String getEmbeddingModel() {
    return embeddingModel;
  }

  public int getEmbeddingDimensions() {
    return embeddingDimensions;
  }

  public EmbeddingJobStatus getStatus() {
    return status;
  }

  public boolean isActive() {
    return active;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public int getChunkCount() {
    return chunkCount;
  }

  public LocalDateTime getRequestedAt() {
    return requestedAt;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public LocalDateTime getFailedAt() {
    return failedAt;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public String getFailureMessage() {
    return failureMessage;
  }
}
