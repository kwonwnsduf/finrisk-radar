package com.finrisk.radar.report;

import static org.assertj.core.api.Assertions.*;

import com.finrisk.radar.usage.UsageType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiReportTest {
  @Test
  void followsTheReportStateMachineAndCompensatesOnlyOnce() {
    AiReport report =
        AiReport.requested(
            1L,
            2L,
            null,
            ReportType.RISK_ANALYSIS,
            "위험을 분석해줘",
            "risk-analysis-v1",
            "hash",
            "key",
            UsageType.RISK_REPORT,
            "usage:key");

    assertThat(report.getStatus()).isEqualTo(ReportStatus.REQUESTED);
    assertThat(report.startOrResume()).isTrue();
    report.advance(ReportStep.AI_ANALYSIS);
    report.complete("분석", "요약", "{\"summary\":\"요약\"}", "configured-model", 10, 20);

    assertThat(report.getStatus()).isEqualTo(ReportStatus.COMPLETED);
    assertThat(report.getCurrentStep()).isEqualTo(ReportStep.COMPLETED);
    assertThat(report.startOrResume()).isFalse();
    assertThat(report.markUsageCompensated()).isTrue();
    assertThat(report.markUsageCompensated()).isFalse();
  }

  @Test
  void keepsTheLastFailureAndUsage() {
    AiReport report =
        AiReport.requested(
            1L,
            null,
            UUID.randomUUID(),
            ReportType.BACKTEST_ANALYSIS,
            null,
            "backtest-analysis-v1",
            "hash",
            null,
            UsageType.AI_AGENT,
            "usage:key");
    report.startOrResume();
    report.addUsage(3, 5, "configured-model");
    report.fail("LLM_TIMEOUT", "Timed out", true);

    assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
    assertThat(report.getInputTokenCount()).isEqualTo(3);
    assertThat(report.getRetryableFailure()).isTrue();
  }
}
