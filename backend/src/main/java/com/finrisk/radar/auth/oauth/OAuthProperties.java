package com.finrisk.radar.auth.oauth;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

	private URI frontendRedirectUri;
	private Duration codeTtl = Duration.ofSeconds(180);

	@PostConstruct
	void validate() {
		if (frontendRedirectUri == null || !frontendRedirectUri.isAbsolute()) {
			throw new IllegalStateException("OAuth frontend redirect URI must be absolute.");
		}
		if (codeTtl == null || codeTtl.isZero() || codeTtl.isNegative()) {
			throw new IllegalStateException("OAuth code TTL must be positive.");
		}
	}

	public URI getFrontendRedirectUri() {
		return frontendRedirectUri;
	}

	public void setFrontendRedirectUri(URI frontendRedirectUri) {
		this.frontendRedirectUri = frontendRedirectUri;
	}

	public Duration getCodeTtl() {
		return codeTtl;
	}

	public void setCodeTtl(Duration codeTtl) {
		this.codeTtl = codeTtl;
	}
}
