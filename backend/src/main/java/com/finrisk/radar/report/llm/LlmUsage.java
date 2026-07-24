package com.finrisk.radar.report.llm;

public record LlmUsage(long inputTokens, long outputTokens) {
  public static final LlmUsage NONE = new LlmUsage(0, 0);
}
