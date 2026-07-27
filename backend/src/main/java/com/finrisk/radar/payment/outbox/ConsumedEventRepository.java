package com.finrisk.radar.payment.outbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, Long> {
  @Modifying
  @Query(
      value =
          "INSERT INTO consumed_events(consumer_name, event_id, consumed_at) "
              + "VALUES (:consumerName, :eventId, CURRENT_TIMESTAMP) "
              + "ON CONFLICT (consumer_name, event_id) DO NOTHING",
      nativeQuery = true)
  int claim(String consumerName, UUID eventId);
}
