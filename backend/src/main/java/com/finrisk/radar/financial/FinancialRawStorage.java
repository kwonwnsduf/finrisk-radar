package com.finrisk.radar.financial;

public interface FinancialRawStorage {
	String storeFinancialStatement(Long assetId, String stockCode, String corpCode, RawDartFinancialStatement raw);
	String storeCorpCodesXml(String xml);
	String storeDebtMaturitySample(String csv);
}
