package com.finrisk.radar.document.kafka;

public final class DocumentTopics {
  public static final String FETCH_REQUESTED = "document-fetch-requested",
      COLLECTED = "document-collected",
      FAILED = "document-collection-failed",
      RISK_ANALYZED = "document-risk-analyzed";

  private DocumentTopics() {}
}
