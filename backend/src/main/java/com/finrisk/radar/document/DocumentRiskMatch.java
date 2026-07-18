package com.finrisk.radar.document;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import com.finrisk.radar.risk.CreditEventType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document_risk_matches")
public class DocumentRiskMatch extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "keyword_code", nullable = false)
  private String keywordCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private CreditEventType eventType;

  @Column(name = "sentence_index", nullable = false)
  private int sentenceIndex;

  @Column(name = "sentence_text", nullable = false, columnDefinition = "text")
  private String sentenceText;

  @Column(name = "match_start_offset", nullable = false)
  private int matchStartOffset;

  @Column(name = "match_end_offset", nullable = false)
  private int matchEndOffset;

  @Column(name = "matched_text", nullable = false)
  private String matchedText;

  @Enumerated(EnumType.STRING)
  @Column(name = "assertion_type", nullable = false)
  private DocumentAssertionType assertionType;

  @Column(name = "source_reliability", nullable = false)
  private BigDecimal sourceReliability;

  @Column(name = "asset_match_confidence", nullable = false)
  private BigDecimal assetMatchConfidence;

  @Column(nullable = false)
  private BigDecimal confidence;

  @Column(name = "extracted_amount")
  private BigDecimal extractedAmount;

  @Column(name = "extracted_currency")
  private String extractedCurrency;

  @Column(name = "amount_original_text")
  private String amountOriginalText;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String evidence;

  @Column(name = "analyzer_version", nullable = false)
  private String analyzerVersion;

  @Column(name = "candidate_id")
  private Long candidateId;

  protected DocumentRiskMatch() {}

  public static DocumentRiskMatch create(
      Long doc,
      Long asset,
      String keyword,
      CreditEventType type,
      int sentenceIndex,
      String sentence,
      int start,
      int end,
      String matched,
      DocumentAssertionType assertion,
      BigDecimal source,
      BigDecimal mapping,
      BigDecimal confidence,
      BigDecimal amount,
      String currency,
      String original,
      String evidence,
      String version) {
    DocumentRiskMatch m = new DocumentRiskMatch();
    m.documentId = doc;
    m.assetId = asset;
    m.keywordCode = keyword;
    m.eventType = type;
    m.sentenceIndex = sentenceIndex;
    m.sentenceText = sentence;
    m.matchStartOffset = start;
    m.matchEndOffset = end;
    m.matchedText = matched;
    m.assertionType = assertion;
    m.sourceReliability = source;
    m.assetMatchConfidence = mapping;
    m.confidence = confidence;
    m.extractedAmount = amount;
    m.extractedCurrency = currency;
    m.amountOriginalText = original;
    m.evidence = evidence;
    m.analyzerVersion = version;
    return m;
  }

  public void candidate(Long id) {
    candidateId = id;
  }

  public Long getId() {
    return id;
  }

  public Long getDocumentId() {
    return documentId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public String getKeywordCode() {
    return keywordCode;
  }

  public CreditEventType getEventType() {
    return eventType;
  }

  public int getSentenceIndex() {
    return sentenceIndex;
  }

  public String getSentenceText() {
    return sentenceText;
  }

  public int getMatchStartOffset() {
    return matchStartOffset;
  }

  public int getMatchEndOffset() {
    return matchEndOffset;
  }

  public String getMatchedText() {
    return matchedText;
  }

  public DocumentAssertionType getAssertionType() {
    return assertionType;
  }

  public BigDecimal getConfidence() {
    return confidence;
  }

  public BigDecimal getExtractedAmount() {
    return extractedAmount;
  }

  public String getExtractedCurrency() {
    return extractedCurrency;
  }

  public String getAmountOriginalText() {
    return amountOriginalText;
  }

  public String getEvidence() {
    return evidence;
  }

  public Long getCandidateId() {
    return candidateId;
  }
}
