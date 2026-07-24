package com.finrisk.radar.report.tool;

import com.finrisk.radar.rag.api.*;
import com.finrisk.radar.rag.service.RagSearchService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DocumentSearchTool {
  private final RagSearchService search;
  private final MeterRegistry meters;

  public DocumentSearchTool(RagSearchService search, MeterRegistry meters) {
    this.search = search;
    this.meters = meters;
  }

  public List<RagSearchResponse> search(Long assetId, String question) {
    List<RagSearchResponse> candidates =
        search.search(new RagSearchRequest(question, assetId, null, null, null, null, 20, 0.65));
    Map<Long, Integer> perDocument = new HashMap<>();
    List<RagSearchResponse> selected = new ArrayList<>();
    for (RagSearchResponse hit : candidates) {
      if (perDocument.getOrDefault(hit.documentId(), 0) >= 2) continue;
      perDocument.merge(hit.documentId(), 1, Integer::sum);
      selected.add(hit);
      if (selected.size() == 8) break;
    }
    meters.counter("rag.context.chunks").increment(selected.size());
    return List.copyOf(selected);
  }
}
