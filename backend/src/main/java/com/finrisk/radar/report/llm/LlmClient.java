package com.finrisk.radar.report.llm;

public interface LlmClient {
  LlmResponse generate(LlmRequest request);

  boolean configured();

  String modelName();
}
