package com.finrisk.radar.report.service;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.rag.api.RagSearchResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportEvidenceValidatorTest {
  @Test
  void rejectsInventedDocumentIds() {
    ReportEvidenceValidator validator = new ReportEvidenceValidator(new ObjectMapper());
    String json =
        "{\"summary\":\"x\",\"evidence\":[{\"documentId\":99,\"chunkIndex\":0,\"riskSignalId\":null}]}";
    assertThatThrownBy(
            () -> validator.parseAndValidate(json, List.<RagSearchResponse>of(), List.of()))
        .isInstanceOf(BusinessException.class);
  }
}
