package com.finrisk.radar.usage;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class UsageLimitService {
  private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
  private static final DefaultRedisScript<Long> RESERVE_SCRIPT =
      new DefaultRedisScript<>(
          "local current = tonumber(redis.call('GET', KEYS[1]) or '0'); if current >="
              + " tonumber(ARGV[1]) then return -1; end; current = redis.call('INCR', KEYS[1]); if"
              + " redis.call('PTTL', KEYS[1]) < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[2]);"
              + " end; return current;",
          Long.class);
  private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
      new DefaultRedisScript<>(
          "local current = tonumber(redis.call('GET', KEYS[1]) or '0'); "
              + "if current <= 1 then redis.call('DEL', KEYS[1]); return 0; end; "
              + "return redis.call('DECR', KEYS[1]);",
          Long.class);

  private final StringRedisTemplate redisTemplate;

  public UsageLimitService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public UsageReservation reserve(Long userId, UsageType type) {
    Instant now = Instant.now();
    String key = key(userId, type, now);
    long ttlMillis = ttl(now).toMillis();
    try {
      Long result =
          redisTemplate.execute(
              RESERVE_SCRIPT,
              List.of(key),
              Integer.toString(type.getFreeLimit()),
              Long.toString(ttlMillis));
      if (result == null) throw new BusinessException(ErrorCode.USAGE_SERVICE_UNAVAILABLE);
      if (result < 0) throw new BusinessException(type.getLimitExceededError());
      return new UsageReservation(key);
    } catch (DataAccessException exception) {
      throw new BusinessException(ErrorCode.USAGE_SERVICE_UNAVAILABLE);
    }
  }

  public void release(UsageReservation reservation) {
    try {
      redisTemplate.execute(RELEASE_SCRIPT, List.of(reservation.key()));
    } catch (DataAccessException exception) {
      throw new BusinessException(ErrorCode.USAGE_SERVICE_UNAVAILABLE);
    }
  }

  public void releaseKey(String reservationKey) {
    release(new UsageReservation(reservationKey));
  }

  public long getUsage(Long userId, UsageType type) {
    try {
      String value = redisTemplate.opsForValue().get(key(userId, type, Instant.now()));
      return value == null ? 0L : Long.parseLong(value);
    } catch (DataAccessException | NumberFormatException exception) {
      throw new BusinessException(ErrorCode.USAGE_SERVICE_UNAVAILABLE);
    }
  }

  String key(Long userId, UsageType type, Instant instant) {
    YearMonth month = YearMonth.from(instant.atZone(SERVICE_ZONE));
    return "usage:" + userId + ":" + type.name() + ":" + month.format(MONTH_FORMAT);
  }

  Duration ttl(Instant instant) {
    YearMonth month = YearMonth.from(instant.atZone(SERVICE_ZONE));
    LocalDate expirationDate = month.plusMonths(1).atDay(2);
    Instant expiration = expirationDate.atStartOfDay(SERVICE_ZONE).toInstant();
    return Duration.between(instant, expiration);
  }

  public record UsageReservation(String key) {}
}
