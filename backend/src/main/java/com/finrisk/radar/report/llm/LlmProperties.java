package com.finrisk.radar.report.llm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.llm.openai")
public record LlmProperties(
    String baseUrl,
    String apiKey,
    String model,
    Duration connectTimeout,
    Duration readTimeout,
    int maxOutputTokens,
    int maxContextCharacters) {
  public LlmProperties {
    if (maxOutputTokens < 1)
      throw new IllegalArgumentException("LLM max output tokens must be positive.");
    if (maxContextCharacters < 1000)
      throw new IllegalArgumentException("LLM context limit is too small.");
  }

  public boolean configured() {
    return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
  }
}
