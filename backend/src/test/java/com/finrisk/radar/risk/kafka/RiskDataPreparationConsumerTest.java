package com.finrisk.radar.risk.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.financial.event.FinancialDataFetchedEvent;
import com.finrisk.radar.risk.RiskCalculationJob;
import com.finrisk.radar.risk.service.RiskCalculationJobService;
import com.finrisk.radar.risk.service.RiskDataPreparationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskDataPreparationConsumerTest {
  @Test
  void startsRiskCalculationAfterCollectedDataIsPrepared() {
    RiskCalculationJobService jobs = mock(RiskCalculationJobService.class);
    AssetRepository assets = mock(AssetRepository.class);
    RiskDataPreparationService preparation = mock(RiskDataPreparationService.class);
    RiskEventPublisher publisher = mock(RiskEventPublisher.class);
    RiskCalculationJob job =
        RiskCalculationJob.collecting(1L, 2L, "reit-risk-v1", LocalDate.of(2026, 7, 20));
    Asset asset = mock(Asset.class);
    when(jobs.get(job.getJobId())).thenReturn(job);
    when(assets.findById(2L)).thenReturn(Optional.of(asset));
    when(jobs.markRequested(job.getJobId())).thenReturn(true);
    RiskDataPreparationConsumer consumer =
        new RiskDataPreparationConsumer(jobs, assets, preparation, publisher);
    UUID collectionJobId = UUID.randomUUID();
    FinancialDataFetchedEvent event =
        new FinancialDataFetchedEvent(
            collectionJobId,
            2L,
            "348950",
            "01234567",
            2026,
            1,
            "CFS",
            false,
            1,
            "s3://bucket/raw.json",
            job.getJobId(),
            Instant.now());

    consumer.collected(event);

    verify(preparation).prepareCollectedData(event, asset);
    verify(publisher).publishRequested(any());
  }
}
