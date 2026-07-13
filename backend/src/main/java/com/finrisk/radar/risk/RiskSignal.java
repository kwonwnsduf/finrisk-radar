package com.finrisk.radar.risk;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "risk_signals")
public class RiskSignal extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "risk_score_id", nullable = false)
  private Long riskScoreId;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RiskCategory category;

  @Column(name = "rule_type", nullable = false)
  private String ruleType;

  @Column(name = "signal_type", nullable = false)
  private String signalType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RiskSeverity severity;

  @Column(nullable = false)
  private int score;

  @Column(nullable = false)
  private String message;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String evidence;

  @Column(name = "source_type")
  private String sourceType;

  @Column(name = "source_id")
  private Long sourceId;

  @Column(name = "detected_at", nullable = false)
  private LocalDateTime detectedAt;

  @Column(name = "deduplication_key", nullable = false, unique = true)
  private String deduplicationKey;

  protected RiskSignal() {}

  public static RiskSignal create(
      Long scoreId,
      Long asset,
      RiskCategory category,
      String rule,
      String type,
      RiskSeverity severity,
      int score,
      String message,
      String evidence,
      String sourceType,
      Long sourceId,
      String key) {
    RiskSignal s = new RiskSignal();
    s.riskScoreId = scoreId;
    s.assetId = asset;
    s.category = category;
    s.ruleType = rule;
    s.signalType = type;
    s.severity = severity;
    s.score = score;
    s.message = message;
    s.evidence = evidence;
    s.sourceType = sourceType;
    s.sourceId = sourceId;
    s.detectedAt = LocalDateTime.now();
    s.deduplicationKey = key;
    return s;
  }

  public Long getId() {
    return id;
  }

  public Long getRiskScoreId() {
    return riskScoreId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public RiskCategory getCategory() {
    return category;
  }

  public String getRuleType() {
    return ruleType;
  }

  public String getSignalType() {
    return signalType;
  }

  public RiskSeverity getSeverity() {
    return severity;
  }

  public int getScore() {
    return score;
  }

  public String getMessage() {
    return message;
  }

  public String getEvidence() {
    return evidence;
  }

  public LocalDateTime getDetectedAt() {
    return detectedAt;
  }
}
