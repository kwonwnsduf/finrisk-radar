package com.finrisk.radar.report.event;

import com.finrisk.radar.report.ReportType;
import java.time.Instant;
import java.util.UUID;

public record ReportFailedEvent(
    UUID reportId, Long userId, ReportType reportType, Instant failedAt) {}
