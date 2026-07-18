package com.finrisk.radar.document.api;

import com.finrisk.radar.document.*;
import com.finrisk.radar.risk.CreditEventType;
import java.math.BigDecimal;

public record DocumentRiskMatchResponse(
    Long id,
    Long assetId,
    String keywordCode,
    CreditEventType eventType,
    int sentenceIndex,
    String sentenceText,
    int matchStartOffset,
    int matchEndOffset,
    String matchedText,
    DocumentAssertionType assertionType,
    BigDecimal confidence,
    BigDecimal extractedAmount,
    String extractedCurrency,
    String amountOriginalText,
    String evidence,
    Long candidateId) {
  public static DocumentRiskMatchResponse from(DocumentRiskMatch m) {
    return new DocumentRiskMatchResponse(
        m.getId(),
        m.getAssetId(),
        m.getKeywordCode(),
        m.getEventType(),
        m.getSentenceIndex(),
        m.getSentenceText(),
        m.getMatchStartOffset(),
        m.getMatchEndOffset(),
        m.getMatchedText(),
        m.getAssertionType(),
        m.getConfidence(),
        m.getExtractedAmount(),
        m.getExtractedCurrency(),
        m.getAmountOriginalText(),
        m.getEvidence(),
        m.getCandidateId());
  }
}
