package com.finrisk.radar.report.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BacktestReportRequest(@NotNull UUID backtestJobId, String question) {}
