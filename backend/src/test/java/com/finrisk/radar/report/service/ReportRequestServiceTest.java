package com.finrisk.radar.report.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.report.*;
import com.finrisk.radar.report.kafka.*;
import com.finrisk.radar.report.llm.LlmClient;
import com.finrisk.radar.report.tool.AssetSearchTool;
import com.finrisk.radar.usage.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportRequestServiceTest {
  @Test
  void explicitlyCompensatesUsageAndFailsReportWhenPublishAfterCommitFails() {
    LlmClient llm = mock(LlmClient.class);
    when(llm.configured()).thenReturn(true);
    UsageLimitService usage = mock(UsageLimitService.class);
    var reservation = new UsageLimitService.UsageReservation("usage:key");
    when(usage.reserve(1L, UsageType.RISK_REPORT)).thenReturn(reservation);
    ReportPersistenceService persistence = mock(ReportPersistenceService.class);
    AiReport report =
        AiReport.requested(
            1L,
            2L,
            null,
            ReportType.RISK_ANALYSIS,
            "위험을 분석해주세요",
            "risk-analysis-v1",
            "hash",
            null,
            UsageType.RISK_REPORT,
            "usage:key");
    when(persistence.create(
            eq(1L),
            eq(2L),
            isNull(),
            eq(ReportType.RISK_ANALYSIS),
            anyString(),
            anyString(),
            isNull(),
            eq(UsageType.RISK_REPORT),
            eq(reservation)))
        .thenReturn(new ReportPersistenceService.Creation(report, false));
    when(persistence.failAndMarkCompensation(
            report.getId(), "REPORT_009", "AI report request could not be published."))
        .thenReturn(Optional.of("usage:key"));
    ReportEventPublisher publisher = mock(ReportEventPublisher.class);
    doThrow(new ReportEventPublishException("failed", new RuntimeException()))
        .when(publisher)
        .requested(any());
    ReportRequestService service =
        new ReportRequestService(llm, usage, persistence, publisher, mock(AssetSearchTool.class));

    assertThatThrownBy(
            () -> service.request(1L, 2L, null, ReportType.RISK_ANALYSIS, "위험을 분석해주세요", null))
        .isInstanceOf(BusinessException.class);

    verify(usage).releaseKey("usage:key");
    verify(persistence)
        .failAndMarkCompensation(
            report.getId(), "REPORT_009", "AI report request could not be published.");
  }

  @Test
  void releasesExtraReservationWhenRequestIsDeduplicated() {
    LlmClient llm = mock(LlmClient.class);
    when(llm.configured()).thenReturn(true);
    UsageLimitService usage = mock(UsageLimitService.class);
    var reservation = new UsageLimitService.UsageReservation("usage:new");
    when(usage.reserve(1L, UsageType.AI_AGENT)).thenReturn(reservation);
    ReportPersistenceService persistence = mock(ReportPersistenceService.class);
    AiReport report =
        AiReport.requested(
            1L,
            null,
            null,
            ReportType.WATCHLIST_SUMMARY,
            "요약",
            "watchlist-summary-v1",
            "hash",
            null,
            UsageType.AI_AGENT,
            "usage:old");
    when(persistence.create(
            any(),
            isNull(),
            isNull(),
            eq(ReportType.WATCHLIST_SUMMARY),
            anyString(),
            anyString(),
            isNull(),
            eq(UsageType.AI_AGENT),
            eq(reservation)))
        .thenReturn(new ReportPersistenceService.Creation(report, true));
    ReportEventPublisher publisher = mock(ReportEventPublisher.class);

    new ReportRequestService(llm, usage, persistence, publisher, mock(AssetSearchTool.class))
        .request(1L, null, null, ReportType.WATCHLIST_SUMMARY, "요약", null);

    verify(usage).release(reservation);
    verifyNoInteractions(publisher);
  }
}
