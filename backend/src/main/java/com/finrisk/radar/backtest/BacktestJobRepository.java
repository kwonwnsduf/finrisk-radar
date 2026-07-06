package com.finrisk.radar.backtest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BacktestJobRepository extends JpaRepository<BacktestJob, UUID> {}
