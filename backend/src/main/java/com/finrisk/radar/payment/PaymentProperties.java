package com.finrisk.radar.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
    boolean enabled,
    String frontendBaseUrl,
    @Valid Provider provider,
    @Valid Lock lock,
    @Valid Recovery recovery,
    @Valid Outbox outbox) {

  public PaymentProperties {
    frontendBaseUrl = blankDefault(frontendBaseUrl, "http://localhost:3000");
    provider = provider == null ? new Provider(null, null, null) : provider;
    lock = lock == null ? new Lock(null) : lock;
    recovery = recovery == null ? new Recovery(null, null) : recovery;
    outbox = outbox == null ? new Outbox(null, null, null) : outbox;
  }

  public record Provider(String baseUrl, Duration connectTimeout, Duration readTimeout) {
    public Provider {
      baseUrl = blankDefault(baseUrl, "https://api.tosspayments.com");
      connectTimeout = durationDefault(connectTimeout, Duration.ofSeconds(3));
      readTimeout = durationDefault(readTimeout, Duration.ofSeconds(10));
    }
  }

  public record Lock(Duration ttl) {
    public Lock {
      ttl = durationDefault(ttl, Duration.ofSeconds(30));
    }
  }

  public record Recovery(Duration fixedDelay, Duration staleAfter) {
    public Recovery {
      fixedDelay = durationDefault(fixedDelay, Duration.ofSeconds(60));
      staleAfter = durationDefault(staleAfter, Duration.ofSeconds(60));
    }
  }

  public record Outbox(Duration fixedDelay, Integer batchSize, Integer maxAttempts) {
    public Outbox {
      fixedDelay = durationDefault(fixedDelay, Duration.ofSeconds(1));
      batchSize = positiveDefault(batchSize, 100);
      maxAttempts = positiveDefault(maxAttempts, 10);
    }
  }

  private static int positiveDefault(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private static Duration durationDefault(Duration value, Duration fallback) {
    return value == null ? fallback : value;
  }

  private static String blankDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
