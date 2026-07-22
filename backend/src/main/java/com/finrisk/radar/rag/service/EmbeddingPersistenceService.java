package com.finrisk.radar.rag.service;

import com.finrisk.radar.document.*;
import com.finrisk.radar.rag.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmbeddingPersistenceService {
  private final DocumentRepository documents;
  private final DocumentEmbeddingJobRepository jobs;
  private final DocumentChunkRepository chunks;

  public EmbeddingPersistenceService(
      DocumentRepository documents,
      DocumentEmbeddingJobRepository jobs,
      DocumentChunkRepository chunks) {
    this.documents = documents;
    this.jobs = jobs;
    this.chunks = chunks;
  }

  @Transactional
  public void complete(UUID jobId, List<DocumentChunk> values, List<float[]> vectors) {
    DocumentEmbeddingJob job = jobs.findById(jobId).orElseThrow();
    Document document = documents.findById(job.getDocumentId()).orElseThrow();
    if (document.getContentVersion() != job.getContentVersion()
        || !document.getContentHash().equals(job.getSourceContentHash())) {
      throw new NonRetryableEmbeddingException(
          "EMBEDDING_DOCUMENT_STALE", "Document content changed during embedding.");
    }
    chunks.replaceForJob(
        jobId,
        job.getDocumentId(),
        job.getContentVersion(),
        job.getEmbeddingModel(),
        job.getEmbeddingDimensions(),
        values,
        vectors);
    jobs.deactivateByDocumentId(job.getDocumentId());
    DocumentEmbeddingJob current = jobs.findById(jobId).orElseThrow();
    current.complete(values.size());
  }
}
