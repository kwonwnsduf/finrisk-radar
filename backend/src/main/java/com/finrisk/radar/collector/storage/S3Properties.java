package com.finrisk.radar.collector.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.market-data.s3")
public record S3Properties(String region, String bucket) {
	public boolean configured() { return text(region) && text(bucket); }
	private static boolean text(String value) { return value != null && !value.isBlank(); }
}
