package com.finrisk.radar.collector.client;

import com.finrisk.radar.marketprice.MarketPriceSource;

public record RawMarketData(MarketPriceSource source, String payload) {}
