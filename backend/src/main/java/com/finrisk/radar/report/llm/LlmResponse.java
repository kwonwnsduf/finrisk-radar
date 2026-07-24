package com.finrisk.radar.report.llm;

public record LlmResponse(String json, String model, LlmUsage usage) {}
