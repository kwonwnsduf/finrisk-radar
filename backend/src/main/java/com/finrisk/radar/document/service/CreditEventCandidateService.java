package com.finrisk.radar.document.service;

import com.finrisk.radar.document.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditEventCandidateService {
  private final CreditEventCandidateRepository candidates;
  private final DocumentRiskMatchRepository matches;

  public CreditEventCandidateService(
      CreditEventCandidateRepository candidates, DocumentRiskMatchRepository matches) {
    this.candidates = candidates;
    this.matches = matches;
  }

  @Transactional
  public CreditEventCandidate attach(
      DocumentRiskMatch match, LocalDate date, com.finrisk.radar.risk.RiskSeverity severity) {
    List<CreditEventCandidate> nearby =
        candidates.findByAssetIdAndEventTypeAndEventDateBetween(
            match.getAssetId(), match.getEventType(), date.minusDays(7), date.plusDays(7));
    CreditEventCandidate candidate =
        nearby.stream()
            .filter(c -> sameIncident(c, match))
            .findFirst()
            .orElseGet(
                () ->
                    candidates.save(
                        CreditEventCandidate.pending(
                            match.getAssetId(),
                            match.getEventType(),
                            date,
                            severity,
                            match.getConfidence(),
                            incident(match, date))));
    match.candidate(candidate.getId());
    matches.save(match);
    candidate.representative(match.getId(), match.getConfidence());
    return candidates.save(candidate);
  }

  private boolean sameIncident(CreditEventCandidate candidate, DocumentRiskMatch next) {
    return matches.findByCandidateIdOrderByConfidenceDesc(candidate.getId()).stream()
        .anyMatch(
            old -> {
              if (old.getExtractedAmount() != null && next.getExtractedAmount() != null)
                return Objects.equals(old.getExtractedCurrency(), next.getExtractedCurrency())
                    && old.getExtractedAmount().compareTo(next.getExtractedAmount()) == 0;
              return jaccard(old.getSentenceText(), next.getSentenceText()) >= 0.65;
            });
  }

  private double jaccard(String a, String b) {
    Set<String> x = tokens(a), y = tokens(b), union = new HashSet<>(x);
    union.addAll(y);
    Set<String> inter = new HashSet<>(x);
    inter.retainAll(y);
    return union.isEmpty() ? 0 : (double) inter.size() / union.size();
  }

  private Set<String> tokens(String s) {
    return new HashSet<>(
        Arrays.asList(s.toLowerCase().replaceAll("[^0-9a-z가-힣 ]", " ").split("\\s+")));
  }

  private String incident(DocumentRiskMatch m, LocalDate d) {
    String basis =
        m.getAssetId()
            + "|"
            + m.getEventType()
            + "|"
            + d.getYear()
            + "-"
            + d.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
            + "|"
            + (m.getExtractedAmount() == null
                ? m.getSentenceText()
                : m.getExtractedCurrency() + m.getExtractedAmount());
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(basis.getBytes(StandardCharsets.UTF_8)))
          .substring(0, 40);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
