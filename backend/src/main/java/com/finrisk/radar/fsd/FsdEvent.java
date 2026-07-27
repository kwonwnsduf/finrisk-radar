package com.finrisk.radar.fsd;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "fsd_events")
public class FsdEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "payment_order_id")
  private Long paymentOrderId;

  @Column(name = "payment_attempt_id")
  private Long paymentAttemptId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "rule_code", nullable = false, length = 80)
  private String ruleCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private FsdPhase phase;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FsdDecision decision;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FsdSeverity severity;

  @Column(nullable = false)
  private int score;

  @Column(nullable = false, length = 500)
  private String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> evidence;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private FsdStatus status;

  @Column(name = "detected_at", nullable = false)
  private LocalDateTime detectedAt;

  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  @Column(name = "reviewed_by")
  private Long reviewedBy;

  @Column(name = "review_note", length = 1000)
  private String reviewNote;

  protected FsdEvent() {}

  static FsdEvent detected(
      Long orderId, Long attemptId, Long userId, FsdPhase phase, RuleResult result) {
    FsdEvent value = new FsdEvent();
    value.paymentOrderId = orderId;
    value.paymentAttemptId = attemptId;
    value.userId = userId;
    value.ruleCode = result.ruleCode();
    value.phase = phase;
    value.decision = result.decision();
    value.severity = result.severity();
    value.score = result.score();
    value.reason = result.reason();
    value.evidence = result.evidence();
    value.status = FsdStatus.OPEN;
    value.detectedAt = LocalDateTime.now();
    return value;
  }

  public void review(FsdStatus next, String note, Long adminId) {
    if (status == FsdStatus.RESOLVED || status == FsdStatus.FALSE_POSITIVE) {
      throw new IllegalStateException("Resolved FSD event cannot transition.");
    }
    status = next;
    reviewNote = note;
    reviewedBy = adminId;
    reviewedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public Long getPaymentOrderId() {
    return paymentOrderId;
  }

  public Long getUserId() {
    return userId;
  }

  public String getRuleCode() {
    return ruleCode;
  }

  public FsdPhase getPhase() {
    return phase;
  }

  public FsdDecision getDecision() {
    return decision;
  }

  public FsdSeverity getSeverity() {
    return severity;
  }

  public int getScore() {
    return score;
  }

  public String getReason() {
    return reason;
  }

  public Map<String, Object> getEvidence() {
    return evidence;
  }

  public FsdStatus getStatus() {
    return status;
  }

  public LocalDateTime getDetectedAt() {
    return detectedAt;
  }

  public LocalDateTime getReviewedAt() {
    return reviewedAt;
  }

  public Long getReviewedBy() {
    return reviewedBy;
  }

  public String getReviewNote() {
    return reviewNote;
  }
}

record RuleResult(
    String ruleCode,
    FsdDecision decision,
    FsdSeverity severity,
    int score,
    String reason,
    Map<String, Object> evidence) {}
