package com.finrisk.radar.collector.log;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.marketprice.MarketPriceSource;
import com.finrisk.radar.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class CollectionLogService {
	private final CollectionLogRepository repository;
	public CollectionLogService(CollectionLogRepository repository) { this.repository = repository; }
	@Transactional
	public CollectionLog createRequested(Long userId, Long assetId, String ticker, LocalDate startDate, LocalDate endDate) {
		return repository.save(CollectionLog.requested(assetId, userId, ticker, startDate, endDate));
	}
	@Transactional public boolean markRunning(UUID id) { return find(id).start(); }
	@Transactional public void markRawStored(UUID id, MarketPriceSource source, String path) { find(id).rawStored(source, path); }
	@Transactional public void markCompleted(UUID id, MarketPriceSource source, int count) { find(id).complete(source, count); }
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailed(UUID id, String message) {
		String safe = message == null || message.isBlank() ? "Collection failed." : message;
		find(id).fail(safe.substring(0, Math.min(safe.length(), 1000)));
	}
	@Transactional(readOnly = true) public CollectionLog getInternal(UUID id) { return find(id); }
	@Transactional(readOnly = true)
	public CollectionJobResponse getForUser(UUID id, Long userId, Role role) {
		CollectionLog log = find(id);
		if (role != Role.ROLE_ADMIN && !log.getRequestedByUserId().equals(userId))
			throw new BusinessException(ErrorCode.COLLECTION_JOB_FORBIDDEN);
		return CollectionJobResponse.from(log);
	}
	private CollectionLog find(UUID id) {
		return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.COLLECTION_JOB_NOT_FOUND));
	}
}
