package com.finrisk.radar.report.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.rag.api.RagSearchResponse;
import com.finrisk.radar.risk.api.RiskSignalResponse;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ReportEvidenceValidator {
  private final ObjectMapper mapper;

  public ReportEvidenceValidator(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public JsonNode parseAndValidate(
      String json, List<RagSearchResponse> documents, List<RiskSignalResponse> signals) {
    try {
      JsonNode root = mapper.readTree(json);
      if (!root.isObject() || !root.path("summary").isTextual())
        throw new BusinessException(ErrorCode.REPORT_INVALID_STRUCTURED_OUTPUT);
      Map<String, RagSearchResponse> chunks = new HashMap<>();
      documents.forEach(d -> chunks.put(d.documentId() + ":" + d.chunkIndex(), d));
      Set<Long> signalIds = new HashSet<>();
      signals.forEach(s -> signalIds.add(s.id()));
      for (JsonNode evidence : root.path("evidence")) {
        if (!evidence.path("documentId").isNull()) {
          String key =
              evidence.path("documentId").asLong() + ":" + evidence.path("chunkIndex").asInt(-1);
          if (!chunks.containsKey(key))
            throw new BusinessException(ErrorCode.REPORT_EVIDENCE_INVALID);
          RagSearchResponse source = chunks.get(key);
          if (evidence instanceof ObjectNode object) {
            object.put("sourceName", source.sourceName());
            object.put("sourceUrl", source.sourceUrl());
            if (source.publishedAt() == null) object.putNull("publishedAt");
            else object.put("publishedAt", source.publishedAt().toString());
            object.put("similarity", source.similarity());
          }
        }
        if (!evidence.path("riskSignalId").isNull()
            && !signalIds.contains(evidence.path("riskSignalId").asLong()))
          throw new BusinessException(ErrorCode.REPORT_EVIDENCE_INVALID);
      }
      return root;
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.REPORT_INVALID_STRUCTURED_OUTPUT);
    }
  }
}
