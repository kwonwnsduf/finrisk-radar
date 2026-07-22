package com.finrisk.radar.rag.service;

public class NonRetryableEmbeddingException extends RuntimeException {
  private final String code;

  public NonRetryableEmbeddingException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public NonRetryableEmbeddingException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
