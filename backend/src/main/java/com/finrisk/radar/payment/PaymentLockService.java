package com.finrisk.radar.payment;

import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class PaymentLockService {
  private static final DefaultRedisScript<Long> RELEASE =
      new DefaultRedisScript<>(
          "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else"
              + " return 0 end",
          Long.class);
  private final StringRedisTemplate redis;
  private final PaymentProperties properties;

  PaymentLockService(StringRedisTemplate redis, PaymentProperties properties) {
    this.redis = redis;
    this.properties = properties;
  }

  LockHandle acquire(String operation, String orderId) {
    String key = "payment:" + operation + ":lock:" + orderId;
    String token = UUID.randomUUID().toString();
    Boolean acquired = redis.opsForValue().setIfAbsent(key, token, properties.lock().ttl());
    return new LockHandle(key, token, Boolean.TRUE.equals(acquired));
  }

  void release(LockHandle lock) {
    if (lock.acquired()) redis.execute(RELEASE, List.of(lock.key()), lock.token());
  }

  record LockHandle(String key, String token, boolean acquired) {}
}
