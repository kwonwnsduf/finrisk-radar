package com.finrisk.radar.usage;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.subscription.PlanType;

public final class UsagePolicy {
	public static final int FREE_WATCHLIST_LIMIT = 5;
	private UsagePolicy() {}

	public static boolean isUnlimited(PlanType plan) {
		if (plan == null) throw new BusinessException(ErrorCode.USER_PLAN_NOT_FOUND);
		return plan == PlanType.PREMIUM || plan == PlanType.ADMIN;
	}

	public static Integer limit(PlanType plan, UsageType type) {
		return isUnlimited(plan) ? null : type.getFreeLimit();
	}

	public static Integer watchlistLimit(PlanType plan) {
		return isUnlimited(plan) ? null : FREE_WATCHLIST_LIMIT;
	}
}
