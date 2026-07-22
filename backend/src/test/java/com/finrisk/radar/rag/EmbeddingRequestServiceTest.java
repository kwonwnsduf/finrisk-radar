package com.finrisk.radar.rag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.finrisk.radar.document.*;
import com.finrisk.radar.rag.embedding.EmbeddingClient;
import com.finrisk.radar.rag.kafka.RagEventPublisher;
import com.finrisk.radar.rag.service.EmbeddingRequestService;
import java.util.*;
import org.junit.jupiter.api.Test;

class EmbeddingRequestServiceTest {
  @Test
  void republishesAnAutomaticallyRetriedPublishFailure() {
    DocumentRepository documents = mock(DocumentRepository.class);
    DocumentEmbeddingJobRepository jobs = mock(DocumentEmbeddingJobRepository.class);
    EmbeddingClient embeddings = mock(EmbeddingClient.class);
    RagEventPublisher publisher = mock(RagEventPublisher.class);
    Document document = mock(Document.class);
    DocumentEmbeddingJob failed =
        DocumentEmbeddingJob.requested(1L, 1, "hash", "text-embedding-3-small", 1536);
    failed.fail("RAG_EVENT_PUBLISH_FAILED", "failed");
    when(document.getId()).thenReturn(1L);
    when(document.getContentVersion()).thenReturn(1);
    when(documents.findById(1L)).thenReturn(Optional.of(document));
    when(embeddings.modelName()).thenReturn("text-embedding-3-small");
    when(embeddings.dimensions()).thenReturn(1536);
    when(jobs.findByDocumentIdAndContentVersionAndEmbeddingModelAndEmbeddingDimensions(
            1L, 1, "text-embedding-3-small", 1536))
        .thenReturn(Optional.of(failed));
    when(jobs.findById(failed.getJobId())).thenReturn(Optional.of(failed));
    when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    EmbeddingRequestService service =
        new EmbeddingRequestService(documents, jobs, embeddings, publisher);

    service.request(1L, UUID.randomUUID(), false);

    verify(publisher).requested(any());
    verify(jobs).save(failed);
  }
}
