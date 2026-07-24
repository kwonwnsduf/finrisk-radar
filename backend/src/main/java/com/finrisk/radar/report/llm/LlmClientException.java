package com.finrisk.radar.report.llm;

public class LlmClientException extends RuntimeException {
  private final String code;
  private final boolean retryable;
  private final LlmUsage usage;

  public LlmClientException(String code, String message, boolean retryable) {
    this(code, message, retryable, LlmUsage.NONE, null);
  }

  public LlmClientException(String code, String message, boolean retryable, Throwable cause) {
    this(code, message, retryable, LlmUsage.NONE, cause);
  }

  public LlmClientException(
      String code, String message, boolean retryable, LlmUsage usage, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.retryable = retryable;
    this.usage = usage == null ? LlmUsage.NONE : usage;
  }

  public String getCode() {
    return code;
  }

  public boolean isRetryable() {
    return retryable;
  }

  public LlmUsage getUsage() {
    return usage;
  }
}
