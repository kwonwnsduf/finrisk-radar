package com.finrisk.radar.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.report.*;
import com.finrisk.radar.report.api.*;
import com.finrisk.radar.user.Role;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportQueryService {
  private final AiReportRepository reports;
  private final ObjectMapper mapper;

  public ReportQueryService(AiReportRepository reports, ObjectMapper mapper) {
    this.reports = reports;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public ReportResponse get(UUID id, Long userId, Role role) {
    AiReport r =
        reports.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    if (role != Role.ROLE_ADMIN && !r.getUserId().equals(userId))
      throw new BusinessException(ErrorCode.REPORT_FORBIDDEN);
    return ReportResponse.from(r, mapper);
  }

  @Transactional(readOnly = true)
  public ReportPageResponse list(
      Long userId, ReportType type, ReportStatus status, int page, int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50));
    Page<AiReport> values =
        type != null && status != null
            ? reports.findByUserIdAndReportTypeAndStatusOrderByRequestedAtDesc(
                userId, type, status, pageable)
            : type != null
                ? reports.findByUserIdAndReportTypeOrderByRequestedAtDesc(userId, type, pageable)
                : status != null
                    ? reports.findByUserIdAndStatusOrderByRequestedAtDesc(userId, status, pageable)
                    : reports.findByUserIdOrderByRequestedAtDesc(userId, pageable);
    return new ReportPageResponse(
        values.getContent().stream().map(r -> ReportResponse.from(r, mapper)).toList(),
        values.getNumber(),
        values.getSize(),
        values.getTotalElements(),
        values.getTotalPages());
  }
}
