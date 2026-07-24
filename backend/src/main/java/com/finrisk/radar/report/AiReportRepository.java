package com.finrisk.radar.report;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AiReportRepository
    extends JpaRepository<AiReport, UUID>, JpaSpecificationExecutor<AiReport> {
  Optional<AiReport> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

  Optional<AiReport>
      findFirstByUserIdAndReportTypeAndRequestFingerprintAndRequestedAtAfterOrderByRequestedAtDesc(
          Long userId, ReportType type, String fingerprint, LocalDateTime after);

  long countByUserIdAndStatusIn(Long userId, Collection<ReportStatus> statuses);

  Page<AiReport> findByUserIdOrderByRequestedAtDesc(Long userId, Pageable pageable);

  Page<AiReport> findByUserIdAndReportTypeOrderByRequestedAtDesc(
      Long userId, ReportType type, Pageable pageable);

  Page<AiReport> findByUserIdAndStatusOrderByRequestedAtDesc(
      Long userId, ReportStatus status, Pageable pageable);

  Page<AiReport> findByUserIdAndReportTypeAndStatusOrderByRequestedAtDesc(
      Long userId, ReportType type, ReportStatus status, Pageable pageable);

  List<AiReport> findTop50ByStatusAndRequestedAtBeforeOrderByRequestedAtAsc(
      ReportStatus status, LocalDateTime before);

  List<AiReport> findTop50ByStatusAndStartedAtBeforeOrderByStartedAtAsc(
      ReportStatus status, LocalDateTime before);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from AiReport r where r.id = :id")
  Optional<AiReport> findByIdForUpdate(@Param("id") UUID id);
}
