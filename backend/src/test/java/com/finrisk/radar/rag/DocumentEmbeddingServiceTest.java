package com.finrisk.radar.rag;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.finrisk.radar.document.*;
import com.finrisk.radar.document.analysis.KoreanSentenceSplitter;
import com.finrisk.radar.document.service.DocumentContentNormalizer;
import com.finrisk.radar.rag.chunk.DocumentChunker;
import com.finrisk.radar.rag.embedding.*;
import com.finrisk.radar.rag.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class DocumentEmbeddingServiceTest {
  @Test
  void embedsInConfiguredBatchesAndPersistsOnlyAfterAllBatches() {
    EmbeddingJobService jobs = mock(EmbeddingJobService.class);
    DocumentRepository documents = mock(DocumentRepository.class);
    EmbeddingPersistenceService persistence = mock(EmbeddingPersistenceService.class);
    EmbeddingClient client = fakeClient();
    OpenAiEmbeddingProperties properties = properties(1);
    Document document = document("문장 하나입니다. 문장 둘입니다. 문장 셋입니다.");
    UUID jobId = UUID.randomUUID();
    when(jobs.start(jobId))
        .thenReturn(
            new EmbeddingJobService.JobSnapshot(
                jobId,
                1L,
                document.getContentVersion(),
                document.getContentHash(),
                client.modelName(),
                1536,
                EmbeddingJobStatus.PROCESSING));
    when(documents.findById(1L)).thenReturn(Optional.of(document));
    DocumentEmbeddingService service =
        new DocumentEmbeddingService(
            jobs,
            documents,
            new DocumentChunker(new KoreanSentenceSplitter(), new DocumentContentNormalizer()),
            client,
            properties,
            persistence,
            new SimpleMeterRegistry());

    service.process(jobId);

    verify(persistence).complete(eq(jobId), argThat(chunks -> !chunks.isEmpty()), anyList());
  }

  @Test
  void completedDuplicateDoesNotCallProvider() {
    EmbeddingJobService jobs = mock(EmbeddingJobService.class);
    DocumentRepository documents = mock(DocumentRepository.class);
    EmbeddingPersistenceService persistence = mock(EmbeddingPersistenceService.class);
    EmbeddingClient client = mock(EmbeddingClient.class);
    UUID jobId = UUID.randomUUID();
    when(jobs.start(jobId))
        .thenReturn(
            new EmbeddingJobService.JobSnapshot(
                jobId,
                1L,
                1,
                "hash",
                "text-embedding-3-small",
                1536,
                EmbeddingJobStatus.COMPLETED));
    DocumentEmbeddingService service =
        new DocumentEmbeddingService(
            jobs,
            documents,
            new DocumentChunker(new KoreanSentenceSplitter(), new DocumentContentNormalizer()),
            client,
            properties(32),
            persistence,
            new SimpleMeterRegistry());

    service.process(jobId);

    verifyNoInteractions(documents, persistence);
    verify(client, never()).embedAll(anyList());
  }

  private EmbeddingClient fakeClient() {
    return new EmbeddingClient() {
      public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
      }

      public List<float[]> embedAll(List<String> texts) {
        return texts.stream().map(text -> new float[1536]).toList();
      }

      public String modelName() {
        return "text-embedding-3-small";
      }

      public int dimensions() {
        return 1536;
      }
    };
  }

  private OpenAiEmbeddingProperties properties(int batchSize) {
    return new OpenAiEmbeddingProperties(
        "https://api.openai.test",
        "key",
        "text-embedding-3-small",
        1536,
        batchSize,
        Duration.ofSeconds(1),
        Duration.ofSeconds(1));
  }

  private Document document(String content) {
    DocumentContentNormalizer normalizer = new DocumentContentNormalizer();
    return Document.create(
        DocumentType.DISCLOSURE,
        DocumentSourceType.OPEN_DART,
        "OpenDART",
        "제목",
        content,
        null,
        "https://dart",
        "receipt",
        java.time.LocalDateTime.now(),
        null,
        normalizer.hash(content),
        normalizer.hash("https://dart"),
        DocumentContentScope.FULL_TEXT);
  }
}
