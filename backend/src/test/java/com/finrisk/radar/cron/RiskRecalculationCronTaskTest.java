package com.finrisk.radar.cron;

import static org.mockito.Mockito.*;

import com.finrisk.radar.document.service.DocumentRiskRecalculationCoordinator;
import org.junit.jupiter.api.Test;

class RiskRecalculationCronTaskTest {
  @Test
  void invokesCoordinatorExactlyOnce() {
    DocumentRiskRecalculationCoordinator coordinator =
        mock(DocumentRiskRecalculationCoordinator.class);

    new RiskRecalculationCronTask(coordinator).run();

    verify(coordinator).retry();
  }
}
