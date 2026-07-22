package com.finrisk.radar.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentEmbeddingJobTest {
  @Test
  void tracksAttemptsAndCompletesAnActiveGeneration() {
    DocumentEmbeddingJob job =
        DocumentEmbeddingJob.requested(1L, 2, "hash", "text-embedding-3-small", 1536);

    job.start();
    job.complete(3);

    assertThat(job.getStatus()).isEqualTo(EmbeddingJobStatus.COMPLETED);
    assertThat(job.isActive()).isTrue();
    assertThat(job.getAttemptCount()).isEqualTo(1);
    assertThat(job.getChunkCount()).isEqualTo(3);
  }

  @Test
  void skippedJobIsTerminalAndInactive() {
    DocumentEmbeddingJob job =
        DocumentEmbeddingJob.skipped(1L, 1, "hash", "text-embedding-3-small", 1536);

    job.start();

    assertThat(job.getStatus()).isEqualTo(EmbeddingJobStatus.SKIPPED);
    assertThat(job.isActive()).isFalse();
    assertThat(job.getAttemptCount()).isZero();
  }
}
