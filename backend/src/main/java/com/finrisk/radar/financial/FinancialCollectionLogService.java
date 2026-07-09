package com.finrisk.radar.financial;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FinancialCollectionLogService {
	private final FinancialCollectionLogRepository repository;
	public FinancialCollectionLogService(FinancialCollectionLogRepository repository) { this.repository = repository; }

	@Transactional
	public FinancialCollectionLog createRequested(Long userId, Long assetId, String stockCode, Integer year, Integer quarter) {
		return repository.save(FinancialCollectionLog.requested(userId, assetId, stockCode, year, quarter));
	}

	@Transactional public boolean markRunning(UUID id) { return find(id).start(); }
	@Transactional public void markRawStored(UUID id, String corpCode, String division, boolean fallbackUsed, String path) {
		find(id).rawStored(corpCode, division, fallbackUsed, path);
	}
	@Transactional public void markCompleted(UUID id, int count) { find(id).complete(count); }
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailed(UUID id, String message) {
		String safe = message == null || message.isBlank() ? "Financial data collection failed." : message;
		find(id).fail(safe.substring(0, Math.min(safe.length(), 1000)));
	}
	@Transactional(readOnly = true) public FinancialCollectionLog getInternal(UUID id) { return find(id); }
	private FinancialCollectionLog find(UUID id) {
		return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_COLLECTION_JOB_NOT_FOUND));
	}
}
