package com.finrisk.radar.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "Invalid input."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_002", "An unexpected error occurred."),
	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_001", "Email is already registered."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_002", "Invalid email or password."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_003", "Authentication is required or token is invalid."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "Refresh token is invalid or expired."),
	AUTH_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_005", "Authentication service is temporarily unavailable."),
	INVALID_OAUTH_CODE(HttpStatus.UNAUTHORIZED, "AUTH_006", "OAuth authorization code is invalid or expired."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_007", "You do not have permission to perform this action."),
	BACKTEST_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "USAGE_001", "Monthly backtest limit exceeded."),
	RISK_REPORT_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "USAGE_002", "Monthly risk report limit exceeded."),
	AI_AGENT_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "USAGE_003", "Monthly AI agent limit exceeded."),
	WATCHLIST_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "USAGE_004", "Watchlist limit exceeded."),
	USER_PLAN_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "USAGE_005", "User plan is unavailable."),
	USAGE_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "USAGE_006", "Usage service is temporarily unavailable."),
	WATCHLIST_ITEM_ALREADY_EXISTS(HttpStatus.CONFLICT, "WATCHLIST_001", "Asset is already in the watchlist."),
	WATCHLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "WATCHLIST_002", "Watchlist item was not found."),
	ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "ASSET_001", "Asset was not found."),
	ASSET_ALREADY_EXISTS(HttpStatus.CONFLICT, "ASSET_002", "Asset with the same ticker and market already exists.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	ErrorCode(HttpStatus httpStatus, String code, String message) {
		this.httpStatus = httpStatus;
		this.code = code;
		this.message = message;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}
}
