package com.finrisk.radar.report.api;

import java.util.List;

public record ReportPageResponse(
    List<ReportResponse> items, int page, int size, long totalElements, int totalPages) {}
