package com.finrisk.radar.marketprice;

public enum MarketPriceSource {
	YAHOO(2),
	CSV(1),
	MANUAL(3);

	private final int priority;

	MarketPriceSource(int priority) {
		this.priority = priority;
	}

	public int priority() {
		return priority;
	}
}
