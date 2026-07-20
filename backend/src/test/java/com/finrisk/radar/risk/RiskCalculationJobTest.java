package com.finrisk.radar.risk;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RiskCalculationJobTest {
  @Test
  void preservesVersionAndTerminalState() {
    RiskCalculationJob job =
        RiskCalculationJob.requested(1L, 2L, "corporate-risk-v1", LocalDate.of(2026, 7, 11));
    assertEquals(RiskCalculationStatus.REQUESTED, job.getStatus());
    assertEquals("corporate-risk-v1", job.getRuleVersion());
    job.fail("RISK_007", "safe");
    assertEquals(RiskCalculationStatus.FAILED, job.getStatus());
    assertEquals("safe", job.getFailureMessage());
  }

  @Test
  void representsDataCollectionAsAnActivePhase() {
    RiskCalculationJob job =
        RiskCalculationJob.collecting(1L, 2L, "reit-risk-v1", LocalDate.of(2026, 7, 20));

    assertEquals(RiskCalculationStatus.COLLECTING, job.getStatus());
    assertEquals("reit-risk-v1", job.getRuleVersion());
  }
}
