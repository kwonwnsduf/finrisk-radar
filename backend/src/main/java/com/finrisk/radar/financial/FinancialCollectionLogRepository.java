package com.finrisk.radar.financial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FinancialCollectionLogRepository extends JpaRepository<FinancialCollectionLog, UUID> {}
