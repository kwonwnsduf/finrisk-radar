package com.finrisk.radar.financial;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dart_corp_codes")
public class DartCorpCode extends BaseTimeEntity {
	@Id
	@Column(name = "corp_code", nullable = false, length = 20)
	private String corpCode;
	@Column(name = "corp_name", nullable = false, length = 200)
	private String corpName;
	@Column(name = "stock_code", length = 20)
	private String stockCode;
	@Column(name = "modify_date", length = 20)
	private String modifyDate;

	protected DartCorpCode() {}

	private DartCorpCode(String corpCode, String corpName, String stockCode, String modifyDate) {
		this.corpCode = corpCode;
		this.corpName = corpName;
		this.stockCode = blankToNull(stockCode);
		this.modifyDate = modifyDate;
	}

	public static DartCorpCode of(String corpCode, String corpName, String stockCode, String modifyDate) {
		return new DartCorpCode(corpCode, corpName, stockCode, modifyDate);
	}

	public void update(String corpName, String stockCode, String modifyDate) {
		this.corpName = corpName;
		this.stockCode = blankToNull(stockCode);
		this.modifyDate = modifyDate;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public String getCorpCode() { return corpCode; }
	public String getCorpName() { return corpName; }
	public String getStockCode() { return stockCode; }
	public String getModifyDate() { return modifyDate; }
}
