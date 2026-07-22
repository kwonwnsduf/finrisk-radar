package com.finrisk.radar.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentContentScopeTest {
  @Test
  void classifiesExtractedContentAtBoundaries() {
    assertThat(DocumentContentScope.forExtractedText("title", "x".repeat(99)))
        .isEqualTo(DocumentContentScope.UNUSABLE);
    assertThat(DocumentContentScope.forExtractedText("title", "x".repeat(100)))
        .isEqualTo(DocumentContentScope.PARTIAL);
    assertThat(DocumentContentScope.forExtractedText("title", "x".repeat(499)))
        .isEqualTo(DocumentContentScope.PARTIAL);
    assertThat(DocumentContentScope.forExtractedText("title", "x".repeat(500)))
        .isEqualTo(DocumentContentScope.FULL_TEXT);
  }

  @Test
  void titleOnlyContentIsUnusable() {
    assertThat(DocumentContentScope.forExtractedText("same title", " same   title "))
        .isEqualTo(DocumentContentScope.UNUSABLE);
  }
}
