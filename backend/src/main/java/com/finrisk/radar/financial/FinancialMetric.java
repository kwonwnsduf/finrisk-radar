package com.finrisk.radar.financial;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "financial_metrics", uniqueConstraints = @UniqueConstraint(
		name = "uk_financial_metrics_asset_period", columnNames = {"asset_id", "year", "quarter"}))
public class FinancialMetric extends BaseTimeEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;
	@Column(nullable = false)
	private Integer year;
	@Column(nullable = false)
	private Integer quarter;
	@Column(precision = 24, scale = 2)
	private BigDecimal revenue;
	@Column(name = "operating_income", precision = 24, scale = 2)
	private BigDecimal operatingIncome;
	@Column(name = "net_income", precision = 24, scale = 2)
	private BigDecimal netIncome;
	@Column(name = "total_debt", precision = 24, scale = 2)
	private BigDecimal totalDebt;
	@Column(name = "total_equity", precision = 24, scale = 2)
	private BigDecimal totalEquity;
	@Column(precision = 24, scale = 2)
	private BigDecimal cash;
	@Column(name = "operating_cash_flow", precision = 24, scale = 2)
	private BigDecimal operatingCashFlow;
	@Column(name = "interest_expense", precision = 24, scale = 2)
	private BigDecimal interestExpense;
	@Column(name = "debt_ratio", precision = 20, scale = 6)
	private BigDecimal debtRatio;
	@Column(name = "interest_coverage_ratio", precision = 20, scale = 6)
	private BigDecimal interestCoverageRatio;

	protected FinancialMetric() {}

	private FinancialMetric(Asset asset, Integer year, Integer quarter) {
		this.asset = asset; this.year = year; this.quarter = quarter;
	}

	public static FinancialMetric create(Asset asset, Integer year, Integer quarter, FinancialMetricValues values) {
		FinancialMetric metric = new FinancialMetric(asset, year, quarter);
		metric.apply(values);
		return metric;
	}

	public void apply(FinancialMetricValues values) {
		this.revenue = values.revenue();
		this.operatingIncome = values.operatingIncome();
		this.netIncome = values.netIncome();
		this.totalDebt = values.totalDebt();
		this.totalEquity = values.totalEquity();
		this.cash = values.cash();
		this.operatingCashFlow = values.operatingCashFlow();
		this.interestExpense = values.interestExpense();
		this.debtRatio = values.debtRatio();
		this.interestCoverageRatio = values.interestCoverageRatio();
	}

	public Long getId() { return id; }
	public Asset getAsset() { return asset; }
	public Integer getYear() { return year; }
	public Integer getQuarter() { return quarter; }
	public BigDecimal getRevenue() { return revenue; }
	public BigDecimal getOperatingIncome() { return operatingIncome; }
	public BigDecimal getNetIncome() { return netIncome; }
	public BigDecimal getTotalDebt() { return totalDebt; }
	public BigDecimal getTotalEquity() { return totalEquity; }
	public BigDecimal getCash() { return cash; }
	public BigDecimal getOperatingCashFlow() { return operatingCashFlow; }
	public BigDecimal getInterestExpense() { return interestExpense; }
	public BigDecimal getDebtRatio() { return debtRatio; }
	public BigDecimal getInterestCoverageRatio() { return interestCoverageRatio; }
}
