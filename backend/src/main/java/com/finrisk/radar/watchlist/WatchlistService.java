package com.finrisk.radar.watchlist;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.usage.UsagePolicy;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WatchlistService {
	private final WatchlistRepository watchlistRepository;
	private final UserRepository userRepository;
	private final AssetRepository assetRepository;

	public WatchlistService(WatchlistRepository watchlistRepository, UserRepository userRepository,
			AssetRepository assetRepository) {
		this.watchlistRepository = watchlistRepository;
		this.userRepository = userRepository;
		this.assetRepository = assetRepository;
	}

	@Transactional
	public WatchlistItemResponse add(Long userId, WatchlistCreateRequest request) {
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		Asset asset = assetRepository.findById(request.assetId())
				.orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
		if (watchlistRepository.existsByUser_IdAndAsset_Id(userId, asset.getId())) {
			throw new BusinessException(ErrorCode.WATCHLIST_ITEM_ALREADY_EXISTS);
		}
		Integer limit = UsagePolicy.watchlistLimit(user.getPlan());
		if (limit != null && watchlistRepository.countByUser_Id(userId) >= limit) {
			throw new BusinessException(ErrorCode.WATCHLIST_LIMIT_EXCEEDED);
		}
		try {
			return WatchlistItemResponse.from(
					watchlistRepository.saveAndFlush(WatchlistItem.create(user, asset)));
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(ErrorCode.WATCHLIST_ITEM_ALREADY_EXISTS);
		}
	}

	@Transactional(readOnly = true)
	public List<WatchlistItemResponse> getAll(Long userId) {
		if (!userRepository.existsById(userId)) throw new BusinessException(ErrorCode.UNAUTHORIZED);
		return watchlistRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
				.map(WatchlistItemResponse::from)
				.toList();
	}

	@Transactional
	public void delete(Long userId, Long itemId) {
		WatchlistItem item = watchlistRepository.findByIdAndUser_Id(itemId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.WATCHLIST_ITEM_NOT_FOUND));
		watchlistRepository.delete(item);
	}
}
