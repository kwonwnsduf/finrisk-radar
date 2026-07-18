package com.finrisk.radar.document;

import static org.assertj.core.api.Assertions.*;

import com.finrisk.radar.document.collector.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DocumentCollectorRegistryTest {
  @Test
  void resolvesCollectorWithoutWorkerBranching() {
    DocumentSourceCollector naver = new Stub(DocumentSourceType.NAVER_NEWS);
    DocumentCollectorRegistry registry = new DocumentCollectorRegistry(List.of(naver));
    assertThat(registry.get(DocumentSourceType.NAVER_NEWS)).isSameAs(naver);
    assertThatThrownBy(() -> registry.get(DocumentSourceType.OPEN_DART))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private record Stub(DocumentSourceType type) implements DocumentSourceCollector {
    public boolean supports(DocumentSourceType source) {
      return source == type;
    }

    public List<CollectedDocument> collect(DocumentCollectionContext context) {
      return List.of();
    }
  }
}
