package com.finrisk.radar.collector.client;

public class MarketDataClientException extends RuntimeException {
	public MarketDataClientException(String message) { super(message); }
	public MarketDataClientException(String message, Throwable cause) { super(message, cause); }
}
