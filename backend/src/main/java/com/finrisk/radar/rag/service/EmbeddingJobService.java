package com.finrisk.radar.rag.service;

import com.finrisk.radar.rag.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class EmbeddingJobService {
  private final DocumentEmbeddingJobRepository jobs;

  public EmbeddingJobService(DocumentEmbeddingJobRepository jobs) {
    this.jobs = jobs;
  }

  @Transactional
  public JobSnapshot start(UUID jobId) {
    DocumentEmbeddingJob job = get(jobId);
    if (!job.terminal()) job.start();
    return JobSnapshot.from(job);
  }

  @Transactional(readOnly = true)
  public DocumentEmbeddingJob get(UUID jobId) {
    return jobs.findById(jobId)
        .orElseThrow(() -> new NoSuchElementException("Embedding job not found."));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void fail(UUID jobId, String code, String message) {
    jobs.findById(jobId).ifPresent(job -> job.fail(code, message));
  }

  @Transactional(readOnly = true)
  public Optional<DocumentEmbeddingJob> latest(Long documentId) {
    return jobs.findTopByDocumentIdOrderByRequestedAtDesc(documentId);
  }

  public record JobSnapshot(
      UUID jobId,
      Long documentId,
      int contentVersion,
      String sourceContentHash,
      String model,
      int dimensions,
      EmbeddingJobStatus status) {
    static JobSnapshot from(DocumentEmbeddingJob job) {
      return new JobSnapshot(
          job.getJobId(),
          job.getDocumentId(),
          job.getContentVersion(),
          job.getSourceContentHash(),
          job.getEmbeddingModel(),
          job.getEmbeddingDimensions(),
          job.getStatus());
    }
  }
}
