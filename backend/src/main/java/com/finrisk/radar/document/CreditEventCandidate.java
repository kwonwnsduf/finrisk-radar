package com.finrisk.radar.document;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import com.finrisk.radar.risk.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "credit_event_candidates")
public class CreditEventCandidate extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private CreditEventType eventType;

  @Column(name = "event_date", nullable = false)
  private LocalDate eventDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RiskSeverity severity;

  @Column(nullable = false)
  private BigDecimal confidence;

  @Column(name = "incident_key", nullable = false)
  private String incidentKey;

  @Column(name = "representative_match_id")
  private Long representativeMatchId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CreditEventCandidateStatus status;

  @Column(name = "reviewed_by_user_id")
  private Long reviewedByUserId;

  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  @Column(name = "review_note", length = 1000)
  private String reviewNote;

  @Column(name = "approved_credit_event_id")
  private Long approvedCreditEventId;

  @Enumerated(EnumType.STRING)
  @Column(name = "recalculation_status", nullable = false)
  private RecalculationStatus recalculationStatus;

  @Column(name = "recalculation_job_id")
  private UUID recalculationJobId;

  protected CreditEventCandidate() {}

  public static CreditEventCandidate pending(
      Long asset,
      CreditEventType type,
      LocalDate date,
      RiskSeverity severity,
      BigDecimal confidence,
      String incident) {
    CreditEventCandidate c = new CreditEventCandidate();
    c.assetId = asset;
    c.eventType = type;
    c.eventDate = date;
    c.severity = severity;
    c.confidence = confidence;
    c.incidentKey = incident;
    c.status = CreditEventCandidateStatus.PENDING_REVIEW;
    c.recalculationStatus = RecalculationStatus.NOT_REQUESTED;
    return c;
  }

  public void representative(Long id, BigDecimal next) {
    representativeMatchId = id;
    if (next.compareTo(confidence) > 0) confidence = next;
  }

  public void approve(Long user, String note, Long event) {
    status = CreditEventCandidateStatus.APPROVED;
    reviewedByUserId = user;
    reviewedAt = LocalDateTime.now();
    reviewNote = note;
    approvedCreditEventId = event;
  }

  public void reject(Long user, String note) {
    status = CreditEventCandidateStatus.REJECTED;
    reviewedByUserId = user;
    reviewedAt = LocalDateTime.now();
    reviewNote = note;
  }

  public void recalculationRequested(UUID job) {
    recalculationStatus = RecalculationStatus.REQUESTED;
    recalculationJobId = job;
  }

  public void recalculationDeferred() {
    recalculationStatus = RecalculationStatus.DEFERRED;
  }

  public Long getId() {
    return id;
  }

  public Long getAssetId() {
    return assetId;
  }

  public CreditEventType getEventType() {
    return eventType;
  }

  public LocalDate getEventDate() {
    return eventDate;
  }

  public RiskSeverity getSeverity() {
    return severity;
  }

  public BigDecimal getConfidence() {
    return confidence;
  }

  public String getIncidentKey() {
    return incidentKey;
  }

  public CreditEventCandidateStatus getStatus() {
    return status;
  }

  public Long getApprovedCreditEventId() {
    return approvedCreditEventId;
  }

  public RecalculationStatus getRecalculationStatus() {
    return recalculationStatus;
  }

  public UUID getRecalculationJobId() {
    return recalculationJobId;
  }

  public Long getReviewedByUserId() {
    return reviewedByUserId;
  }
}
