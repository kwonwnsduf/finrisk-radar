package com.finrisk.radar.financial;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DartCorpCodeService {
	private final DartCorpCodeRepository repository;
	private final DartClient client;
	private final DartCorpCodeParser parser;
	private final FinancialRawStorage storage;

	public DartCorpCodeService(DartCorpCodeRepository repository, DartClient client,
			DartCorpCodeParser parser, FinancialRawStorage storage) {
		this.repository = repository; this.client = client; this.parser = parser; this.storage = storage;
	}

	@Transactional
	public String findCorpCodeByStockCode(String stockCode) {
		String normalized = normalize(stockCode);
		return repository.findFirstByStockCode(normalized)
				.map(DartCorpCode::getCorpCode)
				.orElseGet(() -> refreshAndFind(normalized));
	}

	private String refreshAndFind(String stockCode) {
		String xml = client.downloadCorpCodeXml();
		storage.storeCorpCodesXml(xml);
		List<DartCorpCodeEntry> entries = parser.parse(xml);
		for (DartCorpCodeEntry entry : entries) {
			DartCorpCode corpCode = repository.findById(entry.corpCode())
					.orElseGet(() -> DartCorpCode.of(entry.corpCode(), entry.corpName(), entry.stockCode(), entry.modifyDate()));
			corpCode.update(entry.corpName(), entry.stockCode(), entry.modifyDate());
			repository.save(corpCode);
		}
		return repository.findFirstByStockCode(stockCode)
				.map(DartCorpCode::getCorpCode)
				.orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_CORP_CODE_NOT_FOUND));
	}

	private String normalize(String stockCode) {
		if (stockCode == null || stockCode.isBlank()) throw new BusinessException(ErrorCode.INVALID_INPUT);
		return stockCode.trim();
	}
}
