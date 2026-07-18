package com.finrisk.radar.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.document.*;
import com.finrisk.radar.document.analysis.*;
import com.finrisk.radar.document.analysis.DocumentAmountExtractor.ExtractedAmount;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentRiskAnalysisService {
  public static final String VERSION = "document-risk-v1";
  private final DocumentRepository documents;
  private final DocumentAssetMappingRepository mappings;
  private final DocumentRiskMatchRepository matches;
  private final KoreanSentenceSplitter splitter;
  private final KoreanAssertionClassifier assertions;
  private final DocumentRiskRuleRegistry rules;
  private final DocumentAmountExtractor amounts;
  private final SourceReliabilityPolicy sources;
  private final CreditEventCandidateService candidates;
  private final ObjectMapper mapper;

  public DocumentRiskAnalysisService(
      DocumentRepository d,
      DocumentAssetMappingRepository m,
      DocumentRiskMatchRepository rm,
      KoreanSentenceSplitter s,
      KoreanAssertionClassifier a,
      DocumentRiskRuleRegistry r,
      DocumentAmountExtractor amount,
      SourceReliabilityPolicy source,
      CreditEventCandidateService c,
      ObjectMapper mapper) {
    documents = d;
    mappings = m;
    matches = rm;
    splitter = s;
    assertions = a;
    rules = r;
    amounts = amount;
    sources = source;
    candidates = c;
    this.mapper = mapper;
  }

  @Transactional
  public AnalysisResult analyze(Long documentId) {
    Document d = documents.findById(documentId).orElseThrow();
    int matchCount = 0, candidateCount = 0;
    BigDecimal source = sources.reliability(d);
    for (DocumentAssetMapping mapping : mappings.findByDocumentId(documentId)) {
      for (KoreanSentenceSplitter.Sentence sentence : splitter.split(d.getContent())) {
        for (DocumentRiskRuleRegistry.Rule rule : rules.rules()) {
          Matcher matcher =
              Pattern.compile(rule.regex(), Pattern.CASE_INSENSITIVE).matcher(sentence.text());
          while (matcher.find()) {
            if (matches
                .existsByDocumentIdAndAssetIdAndKeywordCodeAndSentenceIndexAndMatchStartOffset(
                    documentId,
                    mapping.getAssetId(),
                    rule.code(),
                    sentence.index(),
                    matcher.start())) continue;
            DocumentAssertionType assertion = assertions.classify(sentence.text());
            BigDecimal assertionScore =
                switch (assertion) {
                  case AFFIRMED -> BigDecimal.ONE;
                  case UNCERTAIN -> new BigDecimal("0.65");
                  case NEGATED -> BigDecimal.ZERO;
                };
            BigDecimal confidence =
                rule.reliability()
                    .multiply(new BigDecimal("0.35"))
                    .add(source.multiply(new BigDecimal("0.25")))
                    .add(assertionScore.multiply(new BigDecimal("0.20")))
                    .add(mapping.getConfidence().multiply(new BigDecimal("0.20")))
                    .setScale(4, RoundingMode.HALF_UP);
            List<ExtractedAmount> all = amounts.extract(sentence.text());
            ExtractedAmount nearest = amounts.nearest(all, matcher.start());
            String evidence = json(rule, sentence, assertion, all, d, mapping);
            DocumentRiskMatch rm =
                matches.save(
                    DocumentRiskMatch.create(
                        documentId,
                        mapping.getAssetId(),
                        rule.code(),
                        rule.eventType(),
                        sentence.index(),
                        sentence.text(),
                        matcher.start(),
                        matcher.end(),
                        matcher.group(),
                        assertion,
                        source,
                        mapping.getConfidence(),
                        confidence,
                        nearest == null ? null : nearest.amount(),
                        nearest == null ? null : nearest.currency(),
                        nearest == null ? null : nearest.originalText(),
                        evidence,
                        VERSION));
            matchCount++;
            if (assertion != DocumentAssertionType.NEGATED
                && confidence.compareTo(new BigDecimal("0.60")) >= 0) {
              candidates.attach(
                  rm,
                  d.getPublishedAt() == null ? LocalDate.now() : d.getPublishedAt().toLocalDate(),
                  rule.severity());
              candidateCount++;
            }
          }
        }
      }
    }
    return new AnalysisResult(matchCount, candidateCount);
  }

  private String json(
      DocumentRiskRuleRegistry.Rule rule,
      KoreanSentenceSplitter.Sentence sentence,
      DocumentAssertionType assertion,
      List<ExtractedAmount> amounts,
      Document d,
      DocumentAssetMapping mapping) {
    try {
      return mapper.writeValueAsString(
          Map.of(
              "keywordCode",
              rule.code(),
              "sentenceDocumentStart",
              sentence.documentStart(),
              "assertion",
              assertion,
              "amounts",
              amounts,
              "sourceType",
              d.getSourceType(),
              "assetMatchMethod",
              mapping.getMatchMethod(),
              "analyzerVersion",
              VERSION));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public record AnalysisResult(int matchCount, int candidateCount) {}
}
