package com.finrisk.radar.backtest;

import com.finrisk.radar.backtest.engine.BacktestCalculationResult;
import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "backtest_results")
public class BacktestResult extends BaseTimeEntity {
	@Id @Column(name = "job_id", nullable = false, updatable = false)
	private UUID jobId;
	@Column(name = "first_price_date", nullable = false)
	private LocalDate firstPriceDate;
	@Column(name = "last_price_date", nullable = false)
	private LocalDate lastPriceDate;
	@Column(name = "initial_close", nullable = false, precision = 20, scale = 6)
	private BigDecimal initialClose;
	@Column(name = "final_close", nullable = false, precision = 20, scale = 6)
	private BigDecimal finalClose;
	@Column(name = "total_return_rate", nullable = false, precision = 20, scale = 6)
	private BigDecimal totalReturnRate;
	@Column(nullable = false, precision = 20, scale = 6)
	private BigDecimal cagr;
	@Column(nullable = false, precision = 20, scale = 6)
	private BigDecimal mdd;
	@Column(name = "win_rate", nullable = false, precision = 20, scale = 6)
	private BigDecimal winRate;
	@Column(name = "trade_count", nullable = false)
	private Integer tradeCount;
	@Column(name = "sharpe_ratio", nullable = false, precision = 20, scale = 6)
	private BigDecimal sharpeRatio;
	@Column(name = "benchmark_return_rate", nullable = false, precision = 20, scale = 6)
	private BigDecimal benchmarkReturnRate;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "monthly_returns", nullable = false, columnDefinition = "jsonb")
	private String monthlyReturns;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "daily_portfolio_values", nullable = false, columnDefinition = "jsonb")
	private String dailyPortfolioValues;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String trades;

	protected BacktestResult() {}

	public static BacktestResult from(UUID jobId, BacktestCalculationResult calculation,
			String monthlyReturns, String dailyPortfolioValues, String trades) {
		BacktestResult result = new BacktestResult();
		result.jobId = jobId;
		result.firstPriceDate = calculation.firstPriceDate();
		result.lastPriceDate = calculation.lastPriceDate();
		result.initialClose = calculation.initialClose();
		result.finalClose = calculation.finalClose();
		result.totalReturnRate = calculation.totalReturnRate();
		result.cagr = calculation.cagr();
		result.mdd = calculation.mdd();
		result.winRate = calculation.winRate();
		result.tradeCount = calculation.tradeCount();
		result.sharpeRatio = calculation.sharpeRatio();
		result.benchmarkReturnRate = calculation.benchmarkReturnRate();
		result.monthlyReturns = monthlyReturns;
		result.dailyPortfolioValues = dailyPortfolioValues;
		result.trades = trades;
		return result;
	}

	public UUID getJobId() { return jobId; }
	public LocalDate getFirstPriceDate() { return firstPriceDate; }
	public LocalDate getLastPriceDate() { return lastPriceDate; }
	public BigDecimal getInitialClose() { return initialClose; }
	public BigDecimal getFinalClose() { return finalClose; }
	public BigDecimal getTotalReturnRate() { return totalReturnRate; }
	public BigDecimal getCagr() { return cagr; }
	public BigDecimal getMdd() { return mdd; }
	public BigDecimal getWinRate() { return winRate; }
	public Integer getTradeCount() { return tradeCount; }
	public BigDecimal getSharpeRatio() { return sharpeRatio; }
	public BigDecimal getBenchmarkReturnRate() { return benchmarkReturnRate; }
	public String getMonthlyReturns() { return monthlyReturns; }
	public String getDailyPortfolioValues() { return dailyPortfolioValues; }
	public String getTrades() { return trades; }
}
