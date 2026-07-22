package com.finrisk.radar.rag.service;

import com.finrisk.radar.document.*;
import com.finrisk.radar.rag.*;
import com.finrisk.radar.rag.chunk.DocumentChunker;
import com.finrisk.radar.rag.embedding.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentEmbeddingService {
  private static final Logger log = LoggerFactory.getLogger(DocumentEmbeddingService.class);
  private final EmbeddingJobService jobs;
  private final DocumentRepository documents;
  private final DocumentChunker chunker;
  private final EmbeddingClient embeddings;
  private final OpenAiEmbeddingProperties properties;
  private final EmbeddingPersistenceService persistence;
  private final Counter completed;
  private final Counter failed;
  private final Counter apiCalls;
  private final Counter chunksCreated;
  private final Timer duration;

  public DocumentEmbeddingService(
      EmbeddingJobService jobs,
      DocumentRepository documents,
      DocumentChunker chunker,
      EmbeddingClient embeddings,
      OpenAiEmbeddingProperties properties,
      EmbeddingPersistenceService persistence,
      MeterRegistry meters) {
    this.jobs = jobs;
    this.documents = documents;
    this.chunker = chunker;
    this.embeddings = embeddings;
    this.properties = properties;
    this.persistence = persistence;
    completed = meters.counter("rag.embedding.completed");
    failed = meters.counter("rag.embedding.failed");
    apiCalls = meters.counter("rag.embedding.api.calls");
    chunksCreated = meters.counter("rag.embedding.chunks.created");
    duration = meters.timer("rag.embedding.duration");
  }

  public void process(UUID jobId) {
    Timer.Sample sample = Timer.start();
    try {
      var job = jobs.start(jobId);
      if (job.status() == EmbeddingJobStatus.COMPLETED
          || job.status() == EmbeddingJobStatus.SKIPPED) return;
      if (!job.model().equals(embeddings.modelName()) || job.dimensions() != 1536) {
        throw new NonRetryableEmbeddingException(
            "EMBEDDING_CONFIGURATION_CHANGED",
            "Embedding job does not match active configuration.");
      }
      Document document = documents.findById(job.documentId()).orElseThrow();
      if (document.getContentVersion() != job.contentVersion()
          || !document.getContentHash().equals(job.sourceContentHash())) {
        throw new NonRetryableEmbeddingException(
            "EMBEDDING_DOCUMENT_STALE", "Document changed before embedding started.");
      }
      if (!document.getContentScope().embeddable()) return;
      List<DocumentChunk> chunks = chunker.chunk(document.getContent());
      if (chunks.isEmpty()) {
        throw new NonRetryableEmbeddingException(
            "EMBEDDING_CONTENT_EMPTY", "Document produced no embeddable chunks.");
      }
      List<float[]> vectors = new ArrayList<>(chunks.size());
      for (int start = 0; start < chunks.size(); start += properties.batchSize()) {
        int end = Math.min(chunks.size(), start + properties.batchSize());
        List<String> inputs =
            chunks.subList(start, end).stream()
                .map(chunk -> chunker.embeddingInput(document.getTitle(), chunk))
                .toList();
        apiCalls.increment();
        vectors.addAll(embeddings.embedAll(inputs));
      }
      persistence.complete(jobId, chunks, vectors);
      completed.increment();
      chunksCreated.increment(chunks.size());
      log.info(
          "event=rag_embedding_completed jobId={} documentId={} chunks={} batches={}",
          jobId,
          job.documentId(),
          chunks.size(),
          (chunks.size() + properties.batchSize() - 1) / properties.batchSize());
    } catch (EmbeddingClientException exception) {
      failed.increment();
      if (!exception.isRetryable()) {
        throw new NonRetryableEmbeddingException(
            exception.getCode(), exception.getMessage(), exception);
      }
      throw exception;
    } catch (RuntimeException exception) {
      failed.increment();
      throw exception;
    } finally {
      sample.stop(duration);
    }
  }
}
