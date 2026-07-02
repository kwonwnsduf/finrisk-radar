package com.finrisk.radar.watchlist;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.usage.UsagePolicy;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class WatchlistService {
	private final WatchlistRepository watchlistRepository;
	private final UserRepository userRepository;

	public WatchlistService(WatchlistRepository watchlistRepository, UserRepository userRepository) {
		this.watchlistRepository = watchlistRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public WatchlistItemResponse add(Long userId, WatchlistCreateRequest request) {
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		String assetCode = request.assetCode().trim().toUpperCase(Locale.ROOT);
		if (watchlistRepository.existsByUserIdAndAssetCode(userId, assetCode)) {
			throw new BusinessException(ErrorCode.WATCHLIST_ITEM_ALREADY_EXISTS);
		}
		Integer limit = UsagePolicy.watchlistLimit(user.getPlan());
		if (limit != null && watchlistRepository.countByUserId(userId) >= limit) {
			throw new BusinessException(ErrorCode.WATCHLIST_LIMIT_EXCEEDED);
		}
		try {
			return WatchlistItemResponse.from(
					watchlistRepository.saveAndFlush(WatchlistItem.create(userId, assetCode)));
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(ErrorCode.WATCHLIST_ITEM_ALREADY_EXISTS);
		}
	}

	@Transactional(readOnly = true)
	public List<WatchlistItemResponse> getAll(Long userId) {
		if (!userRepository.existsById(userId)) throw new BusinessException(ErrorCode.UNAUTHORIZED);
		return watchlistRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(WatchlistItemResponse::from)
				.toList();
	}

	@Transactional
	public void delete(Long userId, Long itemId) {
		WatchlistItem item = watchlistRepository.findByIdAndUserId(itemId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.WATCHLIST_ITEM_NOT_FOUND));
		watchlistRepository.delete(item);
	}
}
