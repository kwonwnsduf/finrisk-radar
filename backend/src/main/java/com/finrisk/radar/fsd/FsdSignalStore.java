package com.finrisk.radar.fsd;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Short-lived fraud signals. PostgreSQL remains the audit source of truth; Redis only supplies
 * bounded-window counters and distinct-account sets.
 */
@Component
public class FsdSignalStore {
  private final StringRedisTemplate redis;
  private final FsdProperties properties;

  public FsdSignalStore(StringRedisTemplate redis, FsdProperties properties) {
    this.redis = redis;
    this.properties = properties;
  }

  public long recordOrder(Long userId, Instant now) {
    var setting = properties.rapidOrderCreation();
    return addAndCount(
        "fsd:orders:" + userId,
        UUID.randomUUID().toString(),
        now,
        setting.window(),
        setting.redisTtl());
  }

  public long recordFailure(Long userId, Instant now) {
    var setting = properties.failureBurst();
    return addAndCount(
        "fsd:failures:" + userId,
        UUID.randomUUID().toString(),
        now,
        setting.window(),
        setting.redisTtl());
  }

  public long recentFailures(Long userId, Instant now) {
    var setting = properties.failureBurst();
    return trimAndCount("fsd:failures:" + userId, now, setting.window(), setting.redisTtl());
  }

  public long recordIpAccount(String ipHash, Long userId, Instant now) {
    if (ipHash == null || ipHash.isBlank()) return 0;
    var setting = properties.sameIpAccounts();
    return addAndCount(
        "fsd:ip-accounts:" + ipHash, userId.toString(), now, setting.window(), setting.redisTtl());
  }

  private long addAndCount(String key, String member, Instant now, Duration window, Duration ttl) {
    redis.opsForZSet().add(key, member, now.toEpochMilli());
    return trimAndCount(key, now, window, ttl);
  }

  private long trimAndCount(String key, Instant now, Duration window, Duration ttl) {
    redis
        .opsForZSet()
        .removeRangeByScore(key, Double.NEGATIVE_INFINITY, now.minus(window).toEpochMilli());
    redis.expire(key, ttl);
    Long count = redis.opsForZSet().zCard(key);
    return count == null ? 0 : count;
  }
}
