package com.finrisk.radar.financial;

public class DartClientException extends RuntimeException {
	public DartClientException(String message) { super(message); }
	public DartClientException(String message, Throwable cause) { super(message, cause); }
}
