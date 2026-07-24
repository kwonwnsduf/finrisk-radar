package com.finrisk.radar.report.llm;

import com.fasterxml.jackson.databind.JsonNode;

public record LlmRequest(
    String developerPrompt, String userPrompt, String schemaName, JsonNode schema) {}
