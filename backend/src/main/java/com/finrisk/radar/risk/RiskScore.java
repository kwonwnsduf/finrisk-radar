package com.finrisk.radar.risk;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "risk_scores")
public class RiskScore extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "job_id", nullable = false, unique = true)
  private UUID jobId;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "total_score", nullable = false)
  private int totalScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "risk_grade", nullable = false)
  private RiskGrade riskGrade;

  @Enumerated(EnumType.STRING)
  @Column(name = "default_status", nullable = false)
  private DefaultStatus defaultStatus;

  @Column(name = "financial_score")
  private Integer financialScore;

  @Column(name = "liquidity_score")
  private Integer liquidityScore;

  @Column(name = "market_score")
  private Integer marketScore;

  @Column(name = "credit_event_score")
  private Integer creditEventScore;

  @Column(name = "group_contagion_score")
  private Integer groupContagionScore;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "category_statuses", columnDefinition = "jsonb", nullable = false)
  private String categoryStatuses;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_quality", nullable = false)
  private RiskDataQuality dataQuality;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RiskConfidence confidence;

  @Column(name = "required_rule_success_rate", nullable = false)
  private int requiredRuleSuccessRate;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "missing_categories", columnDefinition = "jsonb", nullable = false)
  private String missingCategories;

  @Column(name = "used_financial_metric_count", nullable = false)
  private int financialCount;

  @Column(name = "used_debt_maturity_count", nullable = false)
  private int debtCount;

  @Column(name = "used_market_price_count", nullable = false)
  private int marketCount;

  @Column(name = "used_credit_event_count", nullable = false)
  private int eventCount;

  @Column(name = "used_relationship_count", nullable = false)
  private int relationshipCount;

  @Column(name = "data_as_of_date", nullable = false)
  private LocalDate dataAsOfDate;

  @Column(name = "rule_version", nullable = false)
  private String ruleVersion;

  @Column(name = "calculated_at", nullable = false)
  private LocalDateTime calculatedAt;

  protected RiskScore() {}

  public static RiskScore create(
      UUID job,
      Long asset,
      int total,
      RiskGrade grade,
      DefaultStatus ds,
      Integer fin,
      Integer liq,
      Integer market,
      Integer event,
      Integer group,
      String statuses,
      RiskDataQuality quality,
      RiskConfidence confidence,
      int rate,
      String missing,
      int fc,
      int dc,
      int mc,
      int ec,
      int rc,
      LocalDate asOf,
      String version) {
    RiskScore s = new RiskScore();
    s.jobId = job;
    s.assetId = asset;
    s.totalScore = total;
    s.riskGrade = grade;
    s.defaultStatus = ds;
    s.financialScore = fin;
    s.liquidityScore = liq;
    s.marketScore = market;
    s.creditEventScore = event;
    s.groupContagionScore = group;
    s.categoryStatuses = statuses;
    s.dataQuality = quality;
    s.confidence = confidence;
    s.requiredRuleSuccessRate = rate;
    s.missingCategories = missing;
    s.financialCount = fc;
    s.debtCount = dc;
    s.marketCount = mc;
    s.eventCount = ec;
    s.relationshipCount = rc;
    s.dataAsOfDate = asOf;
    s.ruleVersion = version;
    s.calculatedAt = LocalDateTime.now();
    return s;
  }

  public Long getId() {
    return id;
  }

  public UUID getJobId() {
    return jobId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public int getTotalScore() {
    return totalScore;
  }

  public RiskGrade getRiskGrade() {
    return riskGrade;
  }

  public DefaultStatus getDefaultStatus() {
    return defaultStatus;
  }

  public Integer getFinancialScore() {
    return financialScore;
  }

  public Integer getLiquidityScore() {
    return liquidityScore;
  }

  public Integer getMarketScore() {
    return marketScore;
  }

  public Integer getCreditEventScore() {
    return creditEventScore;
  }

  public Integer getGroupContagionScore() {
    return groupContagionScore;
  }

  public String getCategoryStatuses() {
    return categoryStatuses;
  }

  public RiskDataQuality getDataQuality() {
    return dataQuality;
  }

  public RiskConfidence getConfidence() {
    return confidence;
  }

  public int getRequiredRuleSuccessRate() {
    return requiredRuleSuccessRate;
  }

  public String getMissingCategories() {
    return missingCategories;
  }

  public int getFinancialCount() {
    return financialCount;
  }

  public int getDebtCount() {
    return debtCount;
  }

  public int getMarketCount() {
    return marketCount;
  }

  public int getEventCount() {
    return eventCount;
  }

  public int getRelationshipCount() {
    return relationshipCount;
  }

  public LocalDate getDataAsOfDate() {
    return dataAsOfDate;
  }

  public String getRuleVersion() {
    return ruleVersion;
  }

  public LocalDateTime getCalculatedAt() {
    return calculatedAt;
  }
}
