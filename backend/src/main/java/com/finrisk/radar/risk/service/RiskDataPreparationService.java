package com.finrisk.radar.risk.service;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetType;
import com.finrisk.radar.financial.FinancialDataRequestService;
import com.finrisk.radar.financial.FinancialMetric;
import com.finrisk.radar.financial.FinancialMetricFetchRequest;
import com.finrisk.radar.financial.FinancialMetricRepository;
import com.finrisk.radar.financial.event.FinancialDataFetchedEvent;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.risk.ReitMetric;
import com.finrisk.radar.risk.ReitMetricRepository;
import com.finrisk.radar.risk.RiskCalculationJob;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskDataPreparationService {
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final FinancialMetricRepository financials;
  private final ReitMetricRepository reitMetrics;
  private final FinancialDataRequestService financialRequests;

  public RiskDataPreparationService(
      FinancialMetricRepository financials,
      ReitMetricRepository reitMetrics,
      FinancialDataRequestService financialRequests) {
    this.financials = financials;
    this.reitMetrics = reitMetrics;
    this.financialRequests = financialRequests;
  }

  @Transactional(readOnly = true)
  public boolean hasRequiredData(Asset asset, LocalDate asOf) {
    ReportingPeriod required = latestPublishedPeriod(asOf);
    LocalDate requiredEnd = periodEndDate(required.year(), required.quarter());
    LocalDate oldestAccepted = requiredEnd.minusMonths(15);
    if (asset.getAssetType() == AssetType.REIT) {
      return reitMetrics
          .findFirstByAssetIdAndPeriodLessThanEqualOrderByPeriodDesc(asset.getId(), asOf)
          .filter(metric -> !metric.getPeriod().isBefore(oldestAccepted))
          .isPresent();
    }
    return financials.findByAssetIdOrderByYearDescQuarterDesc(asset.getId()).stream()
        .findFirst()
        .map(
            metric -> {
              LocalDate end =
                  metric.getPeriodEndDate() == null
                      ? periodEndDate(metric.getYear(), metric.getQuarter())
                      : metric.getPeriodEndDate();
              return !end.isBefore(oldestAccepted);
            })
        .orElse(false);
  }

  public void requestCollection(RiskCalculationJob job, Asset asset) {
    ReportingPeriod period = latestPublishedPeriod(job.getDataAsOfDate());
    financialRequests.requestForRisk(
        job.getUserId(),
        new FinancialMetricFetchRequest(asset.getId(), null, period.year(), period.quarter()),
        job.getJobId());
  }

  @Transactional
  public void prepareCollectedData(FinancialDataFetchedEvent event, Asset asset) {
    if (asset.getAssetType() != AssetType.REIT) return;
    FinancialMetric financial =
        financials
            .findByAssetIdAndYearAndQuarter(asset.getId(), event.year(), event.quarter())
            .orElseThrow(() -> new BusinessException(ErrorCode.RISK_FINANCIAL_DATA_NOT_FOUND));
    LocalDate period =
        financial.getPeriodEndDate() == null
            ? periodEndDate(event.year(), event.quarter())
            : financial.getPeriodEndDate();
    if (reitMetrics.existsByAssetIdAndPeriod(asset.getId(), period)) return;

    BigDecimal bookAssetValue = add(financial.getTotalDebt(), financial.getTotalEquity());
    BigDecimal ltv = ratio(financial.getTotalDebt(), bookAssetValue);
    reitMetrics.save(
        ReitMetric.create(
            asset.getId(),
            period,
            ltv,
            bookAssetValue,
            null,
            financial.getTotalDebt(),
            financial.getInterestExpense(),
            financial.getRevenue(),
            null,
            null,
            null,
            null,
            financial.getCash(),
            null,
            null,
            null,
            false,
            "OPEN_DART_FINANCIAL_PROXY",
            "DART:" + event.corpCode() + ":" + event.year() + ":" + event.quarter(),
            financial.getFetchedAt() == null ? LocalDateTime.now() : financial.getFetchedAt()));
  }

  static ReportingPeriod latestPublishedPeriod(LocalDate date) {
    if (!date.isBefore(LocalDate.of(date.getYear(), 11, 16))) {
      return new ReportingPeriod(date.getYear(), 3);
    }
    if (!date.isBefore(LocalDate.of(date.getYear(), 8, 16))) {
      return new ReportingPeriod(date.getYear(), 2);
    }
    if (!date.isBefore(LocalDate.of(date.getYear(), 5, 16))) {
      return new ReportingPeriod(date.getYear(), 1);
    }
    return new ReportingPeriod(date.getYear() - 1, 4);
  }

  private static LocalDate periodEndDate(Integer year, Integer quarter) {
    return switch (quarter) {
      case 1 -> LocalDate.of(year, 3, 31);
      case 2 -> LocalDate.of(year, 6, 30);
      case 3 -> LocalDate.of(year, 9, 30);
      case 4 -> LocalDate.of(year, 12, 31);
      default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
    };
  }

  private static BigDecimal add(BigDecimal left, BigDecimal right) {
    if (left == null && right == null) return null;
    return (left == null ? BigDecimal.ZERO : left).add(right == null ? BigDecimal.ZERO : right);
  }

  private static BigDecimal ratio(BigDecimal debt, BigDecimal assets) {
    if (debt == null || assets == null || assets.signum() <= 0) return null;
    return debt.multiply(HUNDRED).divide(assets, 4, RoundingMode.HALF_UP);
  }

  record ReportingPeriod(int year, int quarter) {}
}
