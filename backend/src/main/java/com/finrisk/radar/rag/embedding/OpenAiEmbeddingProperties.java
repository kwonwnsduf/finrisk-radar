package com.finrisk.radar.rag.embedding;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.rag.openai")
public record OpenAiEmbeddingProperties(
    String baseUrl,
    String apiKey,
    String model,
    int dimensions,
    int batchSize,
    Duration connectTimeout,
    Duration readTimeout) {

  public OpenAiEmbeddingProperties {
    if (dimensions != 1536) {
      throw new IllegalArgumentException("Day 12 embedding dimensions must be 1536.");
    }
    if (batchSize < 1 || batchSize > 2048) {
      throw new IllegalArgumentException("Embedding batch size must be between 1 and 2048.");
    }
  }

  public boolean configured() {
    return apiKey != null && !apiKey.isBlank();
  }
}
