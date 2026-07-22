package com.finrisk.radar.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.finrisk.radar.document.analysis.KoreanSentenceSplitter;
import com.finrisk.radar.document.service.DocumentContentNormalizer;
import com.finrisk.radar.rag.chunk.DocumentChunker;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {
  private final DocumentChunker chunker =
      new DocumentChunker(new KoreanSentenceSplitter(), new DocumentContentNormalizer());

  @Test
  void keepsShortDocumentAsOneChunk() {
    List<DocumentChunk> chunks = chunker.chunk("짧은 한국어 문서입니다.");

    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0).content()).isEqualTo("짧은 한국어 문서입니다.");
    assertThat(chunks.get(0).chunkIndex()).isZero();
  }

  @Test
  void overlapsLastSentenceAndPreservesOrder() {
    String first = "가".repeat(300) + ".";
    String second = "나".repeat(300) + ".";
    String third = "다".repeat(300) + ".";

    List<DocumentChunk> chunks = chunker.chunk(first + second + third);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0).content()).contains(first, second);
    assertThat(chunks.get(1).content()).startsWith(second).endsWith(third);
    assertThat(chunks).allMatch(chunk -> chunk.content().length() <= 1000);
  }

  @Test
  void splitsSingleVeryLongSentenceWithoutExceedingMaximum() {
    List<DocumentChunk> chunks = chunker.chunk("장".repeat(2200));

    assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);
    assertThat(chunks).allMatch(chunk -> !chunk.content().isBlank());
    assertThat(chunks).allMatch(chunk -> chunk.content().length() <= 1000);
  }

  @Test
  void returnsNoChunkForBlankInput() {
    assertThat(chunker.chunk("  ")).isEmpty();
  }
}
