package com.finrisk.radar.rag.api;

import com.finrisk.radar.document.*;
import com.finrisk.radar.risk.CreditEventType;
import java.math.BigDecimal;

public record RagRiskMatchResponse(
    Long id,
    String keywordCode,
    CreditEventType eventType,
    int sentenceIndex,
    String matchedText,
    DocumentAssertionType assertionType,
    BigDecimal confidence) {
  public static RagRiskMatchResponse from(DocumentRiskMatch match) {
    return new RagRiskMatchResponse(
        match.getId(),
        match.getKeywordCode(),
        match.getEventType(),
        match.getSentenceIndex(),
        match.getMatchedText(),
        match.getAssertionType(),
        match.getConfidence());
  }
}
