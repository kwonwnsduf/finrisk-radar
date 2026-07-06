package com.finrisk.radar.collector.log;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CollectionLogRepository extends JpaRepository<CollectionLog, UUID> {}
