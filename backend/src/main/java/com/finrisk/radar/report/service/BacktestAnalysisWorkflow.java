package com.finrisk.radar.report.service;

import com.fasterxml.jackson.databind.*;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.report.*;
import com.finrisk.radar.report.llm.*;
import com.finrisk.radar.report.tool.BacktestResultTool;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BacktestAnalysisWorkflow {
  private final BacktestResultTool backtests;
  private final LlmClient llm;
  private final ReportPromptFactory prompts;
  private final ObjectMapper mapper;

  public BacktestAnalysisWorkflow(
      BacktestResultTool backtests,
      LlmClient llm,
      ReportPromptFactory prompts,
      ObjectMapper mapper) {
    this.backtests = backtests;
    this.llm = llm;
    this.prompts = prompts;
    this.mapper = mapper;
  }

  public GeneratedReport generate(AiReport report) {
    var job = backtests.load(report.getBacktestJobId(), report.getUserId());
    var result = job.result();
    Object context =
        Map.of(
            "jobId",
            job.jobId(),
            "assetId",
            job.assetId(),
            "strategy",
            job.strategyType(),
            "period",
            Map.of("start", job.startDate(), "end", job.endDate()),
            "metrics",
            Map.of(
                "totalReturnRate",
                result.totalReturnRate(),
                "cagr",
                result.cagr(),
                "mdd",
                result.mdd(),
                "winRate",
                result.winRate(),
                "tradeCount",
                result.tradeCount(),
                "sharpeRatio",
                result.sharpeRatio(),
                "benchmarkReturnRate",
                result.benchmarkReturnRate()),
            "monthlyReturns",
            result.monthlyReturns().stream().limit(24).toList(),
            "representativeTrades",
            result.trades().stream().limit(20).toList());
    String json;
    try {
      json = mapper.writeValueAsString(context);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    LlmResponse response =
        llm.generate(
            new LlmRequest(
                prompts.developer(ReportType.BACKTEST_ANALYSIS),
                "Interpret only this verified backtest result.\n" + json,
                prompts.schemaName(ReportType.BACKTEST_ANALYSIS),
                prompts.schema(ReportType.BACKTEST_ANALYSIS)));
    try {
      JsonNode parsed = mapper.readTree(response.json());
      if (!parsed.path("summary").isTextual()) throw new IllegalArgumentException();
      return new GeneratedReport(
          job.strategyType() + " 백테스트 해석",
          parsed.path("summary").asText(),
          response.json(),
          response);
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.REPORT_INVALID_STRUCTURED_OUTPUT);
    }
  }
}
