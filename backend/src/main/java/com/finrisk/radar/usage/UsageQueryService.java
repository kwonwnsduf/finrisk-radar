package com.finrisk.radar.usage;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.subscription.PlanType;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import com.finrisk.radar.watchlist.WatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageQueryService {
	private final UserRepository userRepository;
	private final UsageLimitService usageLimitService;
	private final WatchlistRepository watchlistRepository;

	public UsageQueryService(UserRepository userRepository, UsageLimitService usageLimitService,
			WatchlistRepository watchlistRepository) {
		this.userRepository = userRepository;
		this.usageLimitService = usageLimitService;
		this.watchlistRepository = watchlistRepository;
	}

	@Transactional(readOnly = true)
	public UsageResponse getUsage(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		PlanType plan = user.getPlan();
		if (plan == null) throw new BusinessException(ErrorCode.USER_PLAN_NOT_FOUND);
		return new UsageResponse(
				plan.name(),
				item(userId, plan, UsageType.BACKTEST),
				item(userId, plan, UsageType.RISK_REPORT),
				item(userId, plan, UsageType.AI_AGENT),
				new UsageItemResponse(watchlistRepository.countByUser_Id(userId), UsagePolicy.watchlistLimit(plan))
		);
	}

	private UsageItemResponse item(Long userId, PlanType plan, UsageType type) {
		return new UsageItemResponse(usageLimitService.getUsage(userId, type), UsagePolicy.limit(plan, type));
	}
}
