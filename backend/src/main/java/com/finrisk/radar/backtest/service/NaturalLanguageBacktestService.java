package com.finrisk.radar.backtest.service;

import com.fasterxml.jackson.databind.*;
import com.finrisk.radar.asset.*;
import com.finrisk.radar.backtest.*;
import com.finrisk.radar.backtest.api.*;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.report.llm.*;
import com.finrisk.radar.usage.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class NaturalLanguageBacktestService {
  private final LlmClient llm;
  private final ObjectMapper mapper;
  private final AssetRepository assets;
  private final BacktestRequestValidator validator;
  private final BacktestRequestService requests;
  private final BacktestJobService jobs;
  private final UsageLimitService usage;

  public NaturalLanguageBacktestService(
      LlmClient llm,
      ObjectMapper mapper,
      AssetRepository assets,
      BacktestRequestValidator validator,
      BacktestRequestService requests,
      BacktestJobService jobs,
      UsageLimitService usage) {
    this.llm = llm;
    this.mapper = mapper;
    this.assets = assets;
    this.validator = validator;
    this.requests = requests;
    this.jobs = jobs;
    this.usage = usage;
  }

  public NaturalLanguageBacktestResponse request(
      Long userId, NaturalLanguageBacktestRequest input) {
    if (!llm.configured()) throw new BusinessException(ErrorCode.REPORT_LLM_NOT_CONFIGURED);
    LlmResponse parsed;
    Draft draft;
    try {
      parsed =
          llm.generate(
              new LlmRequest(
                  "Parse the request into only supported backtest fields. Never invent missing"
                      + " asset or dates. Put absent required fields in missingFields. Return JSON"
                      + " only.",
                  input.question(),
                  "backtest_request_v1",
                  schema()));
      draft = mapper.readValue(parsed.json(), Draft.class);
    } catch (LlmClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new BusinessException(ErrorCode.BACKTEST_PARSE_FAILED);
    }
    List<String> missing =
        new ArrayList<>(draft.missingFields() == null ? List.of() : draft.missingFields());
    Asset asset = null;
    List<AssetResponse> candidates = List.of();
    if (input.assetId() != null)
      asset =
          assets
              .findById(input.assetId())
              .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
    else if (draft.assetQuery() == null || draft.assetQuery().isBlank()) missing.add("asset");
    else {
      List<Asset> found = assets.search(draft.assetQuery().trim(), null);
      List<Asset> exact =
          found.stream()
              .filter(
                  a ->
                      a.getName().equalsIgnoreCase(draft.assetQuery().trim())
                          || a.getTicker().equalsIgnoreCase(draft.assetQuery().trim()))
              .toList();
      if (exact.size() == 1) asset = exact.get(0);
      else if (found.size() == 1) asset = found.get(0);
      else {
        candidates = found.stream().limit(10).map(AssetResponse::from).toList();
        missing.add("asset");
      }
    }
    if (draft.strategyType() == null) missing.add("strategyType");
    if (draft.startDate() == null) missing.add("startDate");
    if (draft.endDate() == null) missing.add("endDate");
    if (!missing.isEmpty())
      return NaturalLanguageBacktestResponse.clarification(
          null, missing.stream().distinct().toList(), candidates);
    BacktestCreateRequest request =
        new BacktestCreateRequest(
            asset.getId(),
            draft.strategyType(),
            draft.startDate(),
            draft.endDate(),
            draft.initialCash(),
            draft.buyConditions(),
            draft.sellConditions());
    validator.validate(request);
    UsageLimitService.UsageReservation reservation = usage.reserve(userId, UsageType.BACKTEST);
    AtomicReference<BacktestJob> created = new AtomicReference<>();
    try {
      BacktestCreateResponse response =
          requests.request(
              userId,
              request,
              job -> {
                created.set(job);
                jobs.attachNaturalLanguageMetadata(
                    job.getJobId(),
                    input.question(),
                    parsed.model(),
                    "backtest-request-parser-v1",
                    parsed.usage().inputTokens(),
                    parsed.usage().outputTokens());
                    
              });
      return NaturalLanguageBacktestResponse.accepted(response, request);
    } catch (RuntimeException e) {
      if (created.get() == null) usage.release(reservation);
      throw e;
    }
  }

  private JsonNode schema() {
    try {
      return mapper.readTree(
          """
          {
            "type": "object",
            "additionalProperties": false,
            "properties": {
              "assetQuery": {
                "type": ["string", "null"]
              },
              "strategyType": {
                "type": ["string", "null"],
                "enum": [
                  "BUY_AND_HOLD",
                  "MOVING_AVERAGE",
                  "RSI",
                  "BOLLINGER_BAND",
                  "MACD",
                  "VOLATILITY_BREAKOUT",
                  "DCA",
                  "MA_DEVIATION",
                  "DONCHIAN_CHANNEL",
                  "MOMENTUM",
                  "CUSTOM",
                  null
                ]
              },
              "startDate": {
                "type": ["string", "null"],
                "format": "date"
              },
              "endDate": {
                "type": ["string", "null"],
                "format": "date"
              },
              "initialCash": {
                "type": ["number", "null"]
              },
              "buyConditions": {
                "type": "array",
                "items": {
                  "$ref": "#/$defs/condition"
                }
              },
              "sellConditions": {
                "type": "array",
                "items": {
                  "$ref": "#/$defs/condition"
                }
              },
              "missingFields": {
                "type": "array",
                "items": {
                  "type": "string"
                }
              }
            },
            "required": [
              "assetQuery",
              "strategyType",
              "startDate",
              "endDate",
              "initialCash",
              "buyConditions",
              "sellConditions",
              "missingFields"
            ],
            "$defs": {
              "condition": {
                "type": "object",
                "additionalProperties": false,
                "properties": {
                  "type": {
                    "type": "string"
                  },
                  "period": {
                    "type": ["integer", "null"]
                  },
                  "shortPeriod": {
                    "type": ["integer", "null"]
                  },
                  "longPeriod": {
                    "type": ["integer", "null"]
                  },
                  "signalPeriod": {
                    "type": ["integer", "null"]
                  },
                  "value": {
                    "type": ["number", "null"]
                  }
                },
                "required": [
                  "type",
                  "period",
                  "shortPeriod",
                  "longPeriod",
                  "signalPeriod",
                  "value"
                ]
              }
            }
          }
          """);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public record Draft(
      String assetQuery,
      StrategyType strategyType,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal initialCash,
      List<StrategyCondition> buyConditions,
      List<StrategyCondition> sellConditions,
      List<String> missingFields) {}
}
