package com.finrisk.radar.risk;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(
    name = "reit_metrics",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_reit_metrics_asset_period",
            columnNames = {"asset_id", "period"}))
public class ReitMetric extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(nullable = false)
  private LocalDate period;

  @Column(precision = 10, scale = 4)
  private BigDecimal ltv;

  @Column(name = "book_asset_value", precision = 24, scale = 2)
  private BigDecimal bookAssetValue;

  @Column(name = "appraised_asset_value", precision = 24, scale = 2)
  private BigDecimal appraisedAssetValue;

  @Column(name = "total_borrowings", precision = 24, scale = 2)
  private BigDecimal totalBorrowings;

  @Column(name = "interest_expense", precision = 24, scale = 2)
  private BigDecimal interestExpense;

  @Column(name = "rental_income", precision = 24, scale = 2)
  private BigDecimal rentalIncome;

  @Column(name = "vacancy_rate", precision = 10, scale = 4)
  private BigDecimal vacancyRate;

  @Column(name = "dividend_payout_ratio", precision = 10, scale = 4)
  private BigDecimal dividendPayoutRatio;

  @Column(name = "refinancing_rate", precision = 10, scale = 4)
  private BigDecimal refinancingRate;

  @Column(name = "fx_hedge_settlement", precision = 24, scale = 2)
  private BigDecimal fxHedgeSettlement;

  @Column(name = "available_liquidity", precision = 24, scale = 2)
  private BigDecimal availableLiquidity;

  @Column(name = "cash_trap_threshold", precision = 10, scale = 4)
  private BigDecimal cashTrapThreshold;

  @Column(name = "default_ltv_threshold", precision = 10, scale = 4)
  private BigDecimal defaultLtvThreshold;

  @Column(name = "foreign_cash_dependency_ratio", precision = 10, scale = 4)
  private BigDecimal foreignCashDependencyRatio;

  @Column(name = "cash_trap_flag", nullable = false)
  private boolean cashTrapFlag;

  @Column(name = "source_type", nullable = false, length = 30)
  private String sourceType;

  @Column(name = "source_document_id", length = 200)
  private String sourceDocumentId;

  @Column(name = "fetched_at")
  private LocalDateTime fetchedAt;

  protected ReitMetric() {}

  public static ReitMetric create(
      Long assetId,
      LocalDate period,
      BigDecimal ltv,
      BigDecimal bookAssetValue,
      BigDecimal appraisedAssetValue,
      BigDecimal totalBorrowings,
      BigDecimal interestExpense,
      BigDecimal rentalIncome,
      BigDecimal vacancyRate,
      BigDecimal dividendPayoutRatio,
      BigDecimal refinancingRate,
      BigDecimal fxHedgeSettlement,
      BigDecimal availableLiquidity,
      BigDecimal cashTrapThreshold,
      BigDecimal defaultLtvThreshold,
      BigDecimal foreignCashDependencyRatio,
      boolean cashTrapFlag,
      String sourceType,
      String sourceDocumentId,
      LocalDateTime fetchedAt) {
    ReitMetric metric = new ReitMetric();
    metric.assetId = assetId;
    metric.period = period;
    metric.ltv = ltv;
    metric.bookAssetValue = bookAssetValue;
    metric.appraisedAssetValue = appraisedAssetValue;
    metric.totalBorrowings = totalBorrowings;
    metric.interestExpense = interestExpense;
    metric.rentalIncome = rentalIncome;
    metric.vacancyRate = vacancyRate;
    metric.dividendPayoutRatio = dividendPayoutRatio;
    metric.refinancingRate = refinancingRate;
    metric.fxHedgeSettlement = fxHedgeSettlement;
    metric.availableLiquidity = availableLiquidity;
    metric.cashTrapThreshold = cashTrapThreshold;
    metric.defaultLtvThreshold = defaultLtvThreshold;
    metric.foreignCashDependencyRatio = foreignCashDependencyRatio;
    metric.cashTrapFlag = cashTrapFlag;
    metric.sourceType = sourceType;
    metric.sourceDocumentId = sourceDocumentId;
    metric.fetchedAt = fetchedAt;
    return metric;
  }

  public Long getId() { return id; }
  public Long getAssetId() { return assetId; }
  public LocalDate getPeriod() { return period; }
  public BigDecimal getLtv() { return ltv; }
  public BigDecimal getBookAssetValue() { return bookAssetValue; }
  public BigDecimal getAppraisedAssetValue() { return appraisedAssetValue; }
  public BigDecimal getTotalBorrowings() { return totalBorrowings; }
  public BigDecimal getInterestExpense() { return interestExpense; }
  public BigDecimal getRentalIncome() { return rentalIncome; }
  public BigDecimal getVacancyRate() { return vacancyRate; }
  public BigDecimal getDividendPayoutRatio() { return dividendPayoutRatio; }
  public BigDecimal getRefinancingRate() { return refinancingRate; }
  public BigDecimal getFxHedgeSettlement() { return fxHedgeSettlement; }
  public BigDecimal getAvailableLiquidity() { return availableLiquidity; }
  public BigDecimal getCashTrapThreshold() { return cashTrapThreshold; }
  public BigDecimal getDefaultLtvThreshold() { return defaultLtvThreshold; }
  public BigDecimal getForeignCashDependencyRatio() { return foreignCashDependencyRatio; }
  public boolean isCashTrapFlag() { return cashTrapFlag; }
  public String getSourceType() { return sourceType; }
  public String getSourceDocumentId() { return sourceDocumentId; }
  public LocalDateTime getFetchedAt() { return fetchedAt; }
}
