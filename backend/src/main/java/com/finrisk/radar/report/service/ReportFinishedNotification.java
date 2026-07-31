package com.finrisk.radar.report.service;

import com.finrisk.radar.report.*;
import java.time.Instant;
import java.util.UUID;

public record ReportFinishedNotification(
    UUID reportId, Long userId, ReportType reportType, ReportStatus status, Instant occurredAt) {}
