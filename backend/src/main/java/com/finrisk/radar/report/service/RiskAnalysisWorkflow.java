package com.finrisk.radar.report.service;

import com.fasterxml.jackson.databind.*;
import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.report.*;
import com.finrisk.radar.report.llm.*;
import com.finrisk.radar.report.tool.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RiskAnalysisWorkflow {
  private final AssetSearchTool assets;
  private final RiskDataTool risks;
  private final FinancialRiskTool financials;
  private final DocumentSearchTool documents;
  private final LlmClient llm;
  private final ReportPromptFactory prompts;
  private final ReportEvidenceValidator validator;
  private final ObjectMapper mapper;

  public RiskAnalysisWorkflow(
      AssetSearchTool assets,
      RiskDataTool risks,
      FinancialRiskTool financials,
      DocumentSearchTool documents,
      LlmClient llm,
      ReportPromptFactory prompts,
      ReportEvidenceValidator validator,
      ObjectMapper mapper) {
    this.assets = assets;
    this.risks = risks;
    this.financials = financials;
    this.documents = documents;
    this.llm = llm;
    this.prompts = prompts;
    this.validator = validator;
    this.mapper = mapper;
  }

  public GeneratedReport generate(AiReport report) {
    Asset asset = assets.resolve(report.getAssetId(), report.getQuestion());
    var risk = risks.load(asset.getId());
    var financial = financials.load(asset);
    var docs = documents.search(asset.getId(), report.getQuestion());
    String context =
        json(
            Map.of(
                "asset",
                Map.of(
                    "id",
                    asset.getId(),
                    "name",
                    asset.getName(),
                    "ticker",
                    asset.getTicker(),
                    "assetType",
                    asset.getAssetType()),
                "risk",
                risk,
                "financial",
                financial,
                "documents",
                docs));
    LlmResponse response =
        llm.generate(
            new LlmRequest(
                prompts.developer(ReportType.RISK_ANALYSIS),
                "Question: " + report.getQuestion() + "\nVerified context:\n" + context,
                prompts.schemaName(ReportType.RISK_ANALYSIS),
                prompts.schema(ReportType.RISK_ANALYSIS)));
    JsonNode result = validator.parseAndValidate(response.json(), docs, risk.signals());
    return new GeneratedReport(
        asset.getName() + " 위험 분석", result.path("summary").asText(), json(result), response);
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
