package com.finrisk.radar.risk.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RiskDataPreparationServiceTest {
  @Test
  void choosesTheLatestSafelyPublishedDartPeriod() {
    assertThat(RiskDataPreparationService.latestPublishedPeriod(LocalDate.of(2026, 5, 15)))
        .isEqualTo(new RiskDataPreparationService.ReportingPeriod(2025, 4));
    assertThat(RiskDataPreparationService.latestPublishedPeriod(LocalDate.of(2026, 5, 16)))
        .isEqualTo(new RiskDataPreparationService.ReportingPeriod(2026, 1));
    assertThat(RiskDataPreparationService.latestPublishedPeriod(LocalDate.of(2026, 8, 16)))
        .isEqualTo(new RiskDataPreparationService.ReportingPeriod(2026, 2));
    assertThat(RiskDataPreparationService.latestPublishedPeriod(LocalDate.of(2026, 11, 16)))
        .isEqualTo(new RiskDataPreparationService.ReportingPeriod(2026, 3));
  }
}
