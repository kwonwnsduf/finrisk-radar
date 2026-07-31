package com.finrisk.radar.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class NotificationTest {

  @Test
  void readTimestampMatchesPostgresPrecisionAndIsPreservedOnRepeatedRead() {
    Notification notification = new Notification();
    LocalDateTime first = LocalDateTime.of(2026, 7, 28, 12, 34, 56, 123_456_789);

    notification.markRead(first);

    assertThat(notification.getReadAt()).isEqualTo(first.truncatedTo(ChronoUnit.MICROS));

    notification.markRead(first.plusSeconds(1));

    assertThat(notification.getReadAt()).isEqualTo(first.truncatedTo(ChronoUnit.MICROS));
  }
}
