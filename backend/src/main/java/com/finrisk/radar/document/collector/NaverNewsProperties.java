package com.finrisk.radar.document.collector;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.documents.naver")
public record NaverNewsProperties(String baseUrl, String clientId, String clientSecret) {
  public boolean configured() {
    return clientId != null
        && !clientId.isBlank()
        && clientSecret != null
        && !clientSecret.isBlank();
  }
}
