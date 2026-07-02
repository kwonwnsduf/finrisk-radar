package com.finrisk.radar.usage;

import com.finrisk.radar.global.error.ErrorCode;

public enum UsageType {
	BACKTEST(5, ErrorCode.BACKTEST_LIMIT_EXCEEDED),
	RISK_REPORT(3, ErrorCode.RISK_REPORT_LIMIT_EXCEEDED),
	AI_AGENT(3, ErrorCode.AI_AGENT_LIMIT_EXCEEDED);

	private final int freeLimit;
	private final ErrorCode limitExceededError;

	UsageType(int freeLimit, ErrorCode limitExceededError) {
		this.freeLimit = freeLimit;
		this.limitExceededError = limitExceededError;
	}

	public int getFreeLimit() { return freeLimit; }
	public ErrorCode getLimitExceededError() { return limitExceededError; }
}
