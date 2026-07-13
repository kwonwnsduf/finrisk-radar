package com.finrisk.radar.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "Invalid input."),
  INTERNAL_SERVER_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_002", "An unexpected error occurred."),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_001", "Email is already registered."),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_002", "Invalid email or password."),
  UNAUTHORIZED(
      HttpStatus.UNAUTHORIZED, "AUTH_003", "Authentication is required or token is invalid."),
  INVALID_REFRESH_TOKEN(
      HttpStatus.UNAUTHORIZED, "AUTH_004", "Refresh token is invalid or expired."),
  AUTH_SERVICE_UNAVAILABLE(
      HttpStatus.SERVICE_UNAVAILABLE,
      "AUTH_005",
      "Authentication service is temporarily unavailable."),
  INVALID_OAUTH_CODE(
      HttpStatus.UNAUTHORIZED, "AUTH_006", "OAuth authorization code is invalid or expired."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_007", "You do not have permission to perform this action."),
  BACKTEST_LIMIT_EXCEEDED(
      HttpStatus.TOO_MANY_REQUESTS, "USAGE_001", "Monthly backtest limit exceeded."),
  RISK_REPORT_LIMIT_EXCEEDED(
      HttpStatus.TOO_MANY_REQUESTS, "USAGE_002", "Monthly risk report limit exceeded."),
  AI_AGENT_LIMIT_EXCEEDED(
      HttpStatus.TOO_MANY_REQUESTS, "USAGE_003", "Monthly AI agent limit exceeded."),
  WATCHLIST_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "USAGE_004", "Watchlist limit exceeded."),
  USER_PLAN_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "USAGE_005", "User plan is unavailable."),
  USAGE_SERVICE_UNAVAILABLE(
      HttpStatus.SERVICE_UNAVAILABLE, "USAGE_006", "Usage service is temporarily unavailable."),
  WATCHLIST_ITEM_ALREADY_EXISTS(
      HttpStatus.CONFLICT, "WATCHLIST_001", "Asset is already in the watchlist."),
  WATCHLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "WATCHLIST_002", "Watchlist item was not found."),
  ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "ASSET_001", "Asset was not found."),
  ASSET_ALREADY_EXISTS(
      HttpStatus.CONFLICT, "ASSET_002", "Asset with the same ticker and market already exists."),
  INVALID_DATE_RANGE(
      HttpStatus.BAD_REQUEST, "MARKET_DATA_001", "Start date must not be after end date."),
  UNSUPPORTED_MARKET_ASSET(
      HttpStatus.BAD_REQUEST,
      "MARKET_DATA_002",
      "Only stock and REIT assets support market prices."),
  TICKER_MISMATCH(
      HttpStatus.BAD_REQUEST, "MARKET_DATA_003", "Ticker does not match the asset market ticker."),
  MARKET_DATA_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "MARKET_DATA_004", "Market data is unavailable."),
  RAW_STORAGE_UNAVAILABLE(
      HttpStatus.SERVICE_UNAVAILABLE, "MARKET_DATA_005", "Raw market data storage is unavailable."),
  COLLECTION_JOB_NOT_FOUND(
      HttpStatus.NOT_FOUND, "MARKET_DATA_006", "Collection job was not found."),
  COLLECTION_JOB_FORBIDDEN(
      HttpStatus.FORBIDDEN, "MARKET_DATA_007", "You cannot access this collection job."),
  COLLECTION_REQUEST_FAILED(
      HttpStatus.SERVICE_UNAVAILABLE,
      "MARKET_DATA_008",
      "Collection request could not be published."),
  BACKTEST_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "BACKTEST_001", "Backtest job was not found."),
  BACKTEST_JOB_FORBIDDEN(
      HttpStatus.FORBIDDEN, "BACKTEST_002", "You cannot access this backtest job."),
  BACKTEST_REQUEST_FAILED(
      HttpStatus.SERVICE_UNAVAILABLE, "BACKTEST_003", "Backtest request could not be published."),
  BACKTEST_PRICE_DATA_NOT_FOUND(
      HttpStatus.UNPROCESSABLE_ENTITY,
      "BACKTEST_004",
      "Market price data is unavailable for this backtest."),
  BACKTEST_PRICE_DATA_INVALID(
      HttpStatus.UNPROCESSABLE_ENTITY,
      "BACKTEST_005",
      "Market price data is invalid for this backtest."),
  FINANCIAL_DATA_UNAVAILABLE(
      HttpStatus.BAD_GATEWAY, "FINANCIAL_001", "Financial data is unavailable."),
  DART_API_KEY_MISSING(
      HttpStatus.SERVICE_UNAVAILABLE, "FINANCIAL_002", "DART API key is not configured."),
  FINANCIAL_CORP_CODE_NOT_FOUND(
      HttpStatus.NOT_FOUND, "FINANCIAL_003", "DART corp code was not found for the stock code."),
  FINANCIAL_COLLECTION_REQUEST_FAILED(
      HttpStatus.SERVICE_UNAVAILABLE,
      "FINANCIAL_004",
      "Financial data collection request could not be published."),
  FINANCIAL_COLLECTION_JOB_NOT_FOUND(
      HttpStatus.NOT_FOUND, "FINANCIAL_005", "Financial collection job was not found."),
  RISK_ASSET_NOT_SUPPORTED(
      HttpStatus.UNPROCESSABLE_ENTITY,
      "RISK_001",
      "Only bond issuer assets support corporate risk calculation."),
  RISK_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "RISK_002", "Risk calculation job was not found."),
  RISK_JOB_FORBIDDEN(
      HttpStatus.FORBIDDEN, "RISK_003", "You cannot access this risk calculation job."),
  RISK_SCORE_NOT_FOUND(HttpStatus.NOT_FOUND, "RISK_004", "Risk score was not found."),
  RISK_CALCULATION_ALREADY_RUNNING(
      HttpStatus.CONFLICT, "RISK_005", "A risk calculation is already active for this asset."),
  RISK_FINANCIAL_DATA_NOT_FOUND(
      HttpStatus.UNPROCESSABLE_ENTITY,
      "RISK_006",
      "Financial data required for risk calculation was not found."),
  RISK_CALCULATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "RISK_007", "Risk calculation failed."),
  RISK_CREDIT_EVENT_DUPLICATED(HttpStatus.CONFLICT, "RISK_008", "Credit event already exists."),
  RISK_RELATIONSHIP_INVALID(HttpStatus.BAD_REQUEST, "RISK_009", "Asset relationship is invalid."),
  RISK_KAFKA_PUBLISH_FAILED(
      HttpStatus.SERVICE_UNAVAILABLE,
      "RISK_010",
      "Risk calculation request could not be published."),
  RISK_REIT_METRIC_NOT_FOUND(
      HttpStatus.UNPROCESSABLE_ENTITY,
      "RISK_011",
      "REIT metrics required for risk calculation were not found.");

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
