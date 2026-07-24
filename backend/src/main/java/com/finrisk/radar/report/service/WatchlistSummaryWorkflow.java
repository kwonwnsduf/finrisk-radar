package com.finrisk.radar.report.service;

import com.fasterxml.jackson.databind.*;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.rag.api.RagSearchResponse;
import com.finrisk.radar.report.*;
import com.finrisk.radar.report.llm.*;
import com.finrisk.radar.report.tool.*;
import com.finrisk.radar.risk.api.RiskSignalResponse;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class WatchlistSummaryWorkflow {
  private final WatchlistTool watchlists;
  private final RiskDataTool risks;
  private final DocumentSearchTool documents;
  private final LlmClient llm;
  private final ReportPromptFactory prompts;
  private final ReportEvidenceValidator validator;
  private final ObjectMapper mapper;

  public WatchlistSummaryWorkflow(
      WatchlistTool watchlists,
      RiskDataTool risks,
      DocumentSearchTool documents,
      LlmClient llm,
      ReportPromptFactory prompts,
      ReportEvidenceValidator validator,
      ObjectMapper mapper) {
    this.watchlists = watchlists;
    this.risks = risks;
    this.documents = documents;
    this.llm = llm;
    this.prompts = prompts;
    this.validator = validator;
    this.mapper = mapper;
  }

  public GeneratedReport generate(AiReport report) {
    var items = watchlists.load(report.getUserId());
    if (items.isEmpty()) throw new BusinessException(ErrorCode.REPORT_DATA_INSUFFICIENT);
    List<Object> summaries = new ArrayList<>();
    List<RagSearchResponse> docs = new ArrayList<>();
    List<RiskSignalResponse> signals = new ArrayList<>();
    for (var item : items) {
      try {
        var risk = risks.load(item.assetId());
        summaries.add(Map.of("asset", item, "risk", risk));
        signals.addAll(risk.signals());
        if (docs.size() < 8)
          docs.addAll(
              documents.search(
                  item.assetId(),
                  report.getQuestion() == null ? "주요 위험과 상환 위험" : report.getQuestion()));
      } catch (BusinessException ignored) {
        summaries.add(Map.of("asset", item, "riskDataAvailable", false));
      }
    }
    docs = docs.stream().limit(8).toList();
    String context;
    try {
      context = mapper.writeValueAsString(Map.of("watchlist", summaries, "documents", docs));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    LlmResponse response =
        llm.generate(
            new LlmRequest(
                prompts.developer(ReportType.WATCHLIST_SUMMARY),
                "Summarize the user's watchlist from verified context.\n" + context,
                prompts.schemaName(ReportType.WATCHLIST_SUMMARY),
                prompts.schema(ReportType.WATCHLIST_SUMMARY)));
    JsonNode result = validator.parseAndValidate(response.json(), docs, signals);
    try {
      return new GeneratedReport(
          "관심목록 위험 요약",
          result.path("summary").asText(),
          mapper.writeValueAsString(result),
          response);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
