package com.finrisk.radar.report.service;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.report.*;
import com.finrisk.radar.report.llm.LlmClientException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReportGenerationService {
  private final ReportPersistenceService persistence;
  private final RiskAnalysisWorkflow risk;
  private final BacktestAnalysisWorkflow backtest;
  private final WatchlistSummaryWorkflow watchlist;

  public ReportGenerationService(
      ReportPersistenceService persistence,
      RiskAnalysisWorkflow risk,
      BacktestAnalysisWorkflow backtest,
      WatchlistSummaryWorkflow watchlist) {
    this.persistence = persistence;
    this.risk = risk;
    this.backtest = backtest;
    this.watchlist = watchlist;
  }

  public void generate(UUID id) {
    if (!persistence.begin(id)) return;
    AiReport report = persistence.get(id);
    try {
      persistence.advance(id, ReportStep.AI_ANALYSIS);
      GeneratedReport generated =
          switch (report.getReportType()) {
            case RISK_ANALYSIS -> risk.generate(report);
            case BACKTEST_ANALYSIS -> backtest.generate(report);
            case WATCHLIST_SUMMARY -> watchlist.generate(report);
          };
      persistence.complete(id, generated);
    } catch (LlmClientException exception) {
      persistence.addUsage(
          id, exception.getUsage().inputTokens(), exception.getUsage().outputTokens(), null);
      if (!exception.isRetryable())
        throw new NonRetryableReportException(
            exception.getCode(), exception.getMessage(), exception);
      throw new ReportGenerationException(
          exception.getCode(), exception.getMessage(), true, exception);
    } catch (BusinessException exception) {
      throw new NonRetryableReportException(
          exception.getErrorCode().getCode(), exception.getMessage(), exception);
    } catch (RuntimeException exception) {
      throw new NonRetryableReportException(
          "REPORT_GENERATION_FAILED", "Report generation failed.", exception);
    }
  }
}
