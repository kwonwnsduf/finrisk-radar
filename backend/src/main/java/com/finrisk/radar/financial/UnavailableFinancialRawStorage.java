package com.finrisk.radar.financial;

import com.finrisk.radar.collector.storage.RawStorageException;

public class UnavailableFinancialRawStorage implements FinancialRawStorage {
	@Override public String storeFinancialStatement(Long assetId, String stockCode, String corpCode, RawDartFinancialStatement raw) {
		throw new RawStorageException("Raw financial data storage is unavailable.");
	}
	@Override public String storeCorpCodesXml(String xml) {
		throw new RawStorageException("Raw financial data storage is unavailable.");
	}
	@Override public String storeDebtMaturitySample(String csv) {
		throw new RawStorageException("Raw financial data storage is unavailable.");
	}
}
