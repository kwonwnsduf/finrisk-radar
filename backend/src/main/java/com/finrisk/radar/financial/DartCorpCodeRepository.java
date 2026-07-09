package com.finrisk.radar.financial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DartCorpCodeRepository extends JpaRepository<DartCorpCode, String> {
	Optional<DartCorpCode> findFirstByStockCode(String stockCode);
}
