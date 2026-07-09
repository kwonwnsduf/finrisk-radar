package com.finrisk.radar.financial;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.dart")
public record DartProperties(String baseUrl, String apiKey) {
	public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
}
