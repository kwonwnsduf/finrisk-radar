package com.finrisk.radar.rag.service;

import com.finrisk.radar.document.*;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.rag.*;
import com.finrisk.radar.rag.embedding.EmbeddingClient;
import com.finrisk.radar.rag.event.EmbeddingRequestedEvent;
import com.finrisk.radar.rag.kafka.RagEventPublisher;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingRequestService {
  private final DocumentRepository documents;
  private final DocumentEmbeddingJobRepository jobs;
  private final EmbeddingClient embeddings;
  private final RagEventPublisher publisher;

  public EmbeddingRequestService(
      DocumentRepository documents,
      DocumentEmbeddingJobRepository jobs,
      EmbeddingClient embeddings,
      RagEventPublisher publisher) {
    this.documents = documents;
    this.jobs = jobs;
    this.embeddings = embeddings;
    this.publisher = publisher;
  }

  public DocumentEmbeddingJob request(Long documentId, UUID correlationId, boolean retryFailed) {
    Document document =
        documents
            .findById(documentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RAG_DOCUMENT_NOT_FOUND));
    DocumentEmbeddingJob job = createOrGet(document);
    if (job.getStatus() == EmbeddingJobStatus.SKIPPED
        || job.getStatus() == EmbeddingJobStatus.COMPLETED
        || (job.getStatus() == EmbeddingJobStatus.FAILED
            && !retryFailed
            && !"RAG_EVENT_PUBLISH_FAILED".equals(job.getFailureCode()))) return job;
    if (job.getStatus() == EmbeddingJobStatus.FAILED) reset(job.getJobId());
    try {
      publisher.requested(
          new EmbeddingRequestedEvent(
              1,
              correlationId == null ? UUID.randomUUID() : correlationId,
              job.getJobId(),
              documentId,
              document.getContentVersion(),
              embeddings.modelName(),
              embeddings.dimensions(),
              Instant.now()));
    } catch (RuntimeException exception) {
      failPublish(job.getJobId());
      throw new BusinessException(ErrorCode.RAG_EMBEDDING_REQUEST_FAILED);
    }
    return jobs.findById(job.getJobId()).orElseThrow();
  }

  protected DocumentEmbeddingJob createOrGet(Document document) {
    var existing =
        jobs.findByDocumentIdAndContentVersionAndEmbeddingModelAndEmbeddingDimensions(
            document.getId(),
            document.getContentVersion(),
            embeddings.modelName(),
            embeddings.dimensions());
    if (existing.isPresent()) return existing.get();
    DocumentEmbeddingJob created =
        document.getContentScope().embeddable()
            ? DocumentEmbeddingJob.requested(
                document.getId(),
                document.getContentVersion(),
                document.getContentHash(),
                embeddings.modelName(),
                embeddings.dimensions())
            : DocumentEmbeddingJob.skipped(
                document.getId(),
                document.getContentVersion(),
                document.getContentHash(),
                embeddings.modelName(),
                embeddings.dimensions());
    try {
      return jobs.saveAndFlush(created);
    } catch (DataIntegrityViolationException exception) {
      return jobs.findByDocumentIdAndContentVersionAndEmbeddingModelAndEmbeddingDimensions(
              document.getId(),
              document.getContentVersion(),
              embeddings.modelName(),
              embeddings.dimensions())
          .orElseThrow(() -> exception);
    }
  }

  protected void reset(UUID jobId) {
    DocumentEmbeddingJob job = jobs.findById(jobId).orElseThrow();
    job.requestedAgain();
    jobs.save(job);
  }

  protected void failPublish(UUID jobId) {
    jobs.findById(jobId)
        .ifPresent(
            job -> {
              job.fail("RAG_EVENT_PUBLISH_FAILED", "Embedding request could not be published.");
              jobs.save(job);
            });
  }
}
