package com.finrisk.radar.backtest;

import com.finrisk.radar.backtest.engine.BacktestCalculationResult;
import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

	protected BacktestResult() {}

	public static BacktestResult from(UUID jobId, BacktestCalculationResult calculation) {
		BacktestResult result = new BacktestResult();
		result.jobId = jobId;
		result.firstPriceDate = calculation.firstPriceDate();
		result.lastPriceDate = calculation.lastPriceDate();
		result.initialClose = calculation.initialClose();
		result.finalClose = calculation.finalClose();
		result.totalReturnRate = calculation.totalReturnRate();
		return result;
	}

	public UUID getJobId() { return jobId; }
	public LocalDate getFirstPriceDate() { return firstPriceDate; }
	public LocalDate getLastPriceDate() { return lastPriceDate; }
	public BigDecimal getInitialClose() { return initialClose; }
	public BigDecimal getFinalClose() { return finalClose; }
	public BigDecimal getTotalReturnRate() { return totalReturnRate; }
}
