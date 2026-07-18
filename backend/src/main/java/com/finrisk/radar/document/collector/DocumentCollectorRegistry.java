package com.finrisk.radar.document.collector;

import com.finrisk.radar.document.DocumentSourceType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DocumentCollectorRegistry {
  private final List<DocumentSourceCollector> collectors;

  public DocumentCollectorRegistry(List<DocumentSourceCollector> collectors) {
    this.collectors = List.copyOf(collectors);
  }

  public DocumentSourceCollector get(DocumentSourceType type) {
    return collectors.stream()
        .filter(c -> c.supports(type))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported document source: " + type));
  }
}
