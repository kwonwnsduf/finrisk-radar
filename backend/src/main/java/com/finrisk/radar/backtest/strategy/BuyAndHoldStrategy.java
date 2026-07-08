package com.finrisk.radar.backtest.strategy;

import com.finrisk.radar.backtest.StrategyType;

import java.util.List;

import com.finrisk.radar.marketprice.MarketPriceResponse;

public class BuyAndHoldStrategy extends SignalStrategy {
	@Override public StrategyType supports() { return StrategyType.BUY_AND_HOLD; }
	@Override protected boolean buySignal(int index, List<MarketPriceResponse> prices) { return index == 0; }
	@Override protected boolean sellSignal(int index, List<MarketPriceResponse> prices) { return false; }
}
