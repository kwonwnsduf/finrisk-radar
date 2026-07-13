package com.finrisk.radar.risk;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "credit_events")
public class CreditEvent extends BaseTimeEntity {
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

  @Column(name = "source_type", nullable = false)
  private String sourceType;

  @Column(name = "source_name")
  private String sourceName;

  @Column(name = "source_document_id")
  private String sourceDocumentId;

  @Column(length = 2000)
  private String description;

  @Column(name = "incident_key")
  private String incidentKey;

  @Column(name = "external_event_key", nullable = false, unique = true)
  private String externalEventKey;

  protected CreditEvent() {}

  public static CreditEvent create(
      Long assetId,
      CreditEventType type,
      LocalDate date,
      RiskSeverity severity,
      String sourceType,
      String sourceName,
      String documentId,
      String description,
      String incidentKey,
      String externalKey) {
    CreditEvent e = new CreditEvent();
    e.assetId = assetId;
    e.eventType = type;
    e.eventDate = date;
    e.severity = severity;
    e.sourceType = sourceType;
    e.sourceName = sourceName;
    e.sourceDocumentId = documentId;
    e.description = description;
    e.incidentKey = incidentKey;
    e.externalEventKey = externalKey;
    return e;
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

  public String getSourceType() {
    return sourceType;
  }

  public String getSourceName() {
    return sourceName;
  }

  public String getSourceDocumentId() {
    return sourceDocumentId;
  }

  public String getDescription() {
    return description;
  }

  public String getIncidentKey() {
    return incidentKey;
  }

  public String getExternalEventKey() {
    return externalEventKey;
  }
}
