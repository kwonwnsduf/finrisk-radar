package com.finrisk.radar.backtest.kafka;

public final class BacktestTopics {
	public static final String REQUESTED = "backtest-requested";
	public static final String COMPLETED = "backtest-completed";
	public static final String FAILED = "backtest-failed";
	private BacktestTopics() {}
}
