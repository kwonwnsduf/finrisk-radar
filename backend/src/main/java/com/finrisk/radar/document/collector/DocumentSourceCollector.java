package com.finrisk.radar.document.collector;

import com.finrisk.radar.document.DocumentSourceType;
import java.util.List;

public interface DocumentSourceCollector {
  boolean supports(DocumentSourceType sourceType);

  List<CollectedDocument> collect(DocumentCollectionContext context);
}
