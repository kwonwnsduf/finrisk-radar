package com.finrisk.radar.financial;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "debt_maturities", uniqueConstraints = @UniqueConstraint(
		name = "uk_debt_maturities_sample", columnNames = {"asset_id", "maturity_date", "amount", "debt_type"}))
public class DebtMaturity extends BaseTimeEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;
	@Column(name = "maturity_date", nullable = false)
	private LocalDate maturityDate;
	@Column(nullable = false, precision = 24, scale = 2)
	private BigDecimal amount;
	@Enumerated(EnumType.STRING)
	@Column(name = "debt_type", nullable = false, length = 40)
	private DebtType debtType;
	@Column(name = "interest_rate", precision = 10, scale = 4)
	private BigDecimal interestRate;
	@Column(nullable = false, length = 10)
	private String currency;
	@Column(name = "is_short_term", nullable = false)
	private boolean shortTerm;

	protected DebtMaturity() {}

	private DebtMaturity(Asset asset, LocalDate maturityDate, BigDecimal amount, DebtType debtType,
			BigDecimal interestRate, String currency, boolean shortTerm) {
		this.asset = asset; this.maturityDate = maturityDate; this.amount = amount; this.debtType = debtType;
		this.interestRate = interestRate; this.currency = currency; this.shortTerm = shortTerm;
	}

	public static DebtMaturity create(Asset asset, LocalDate maturityDate, BigDecimal amount, DebtType debtType,
			BigDecimal interestRate, String currency, boolean shortTerm) {
		return new DebtMaturity(asset, maturityDate, amount, debtType, interestRate, currency, shortTerm);
	}

	public Long getId() { return id; }
	public Asset getAsset() { return asset; }
	public LocalDate getMaturityDate() { return maturityDate; }
	public BigDecimal getAmount() { return amount; }
	public DebtType getDebtType() { return debtType; }
	public BigDecimal getInterestRate() { return interestRate; }
	public String getCurrency() { return currency; }
	public boolean isShortTerm() { return shortTerm; }
}
