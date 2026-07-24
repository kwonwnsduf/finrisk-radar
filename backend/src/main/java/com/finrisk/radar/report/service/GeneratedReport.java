package com.finrisk.radar.report.service;

import com.finrisk.radar.report.llm.LlmResponse;

public record GeneratedReport(String title, String summary, String json, LlmResponse llm) {}
