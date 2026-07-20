package com.finrisk.radar.risk.service;

import static org.mockito.Mockito.*;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.asset.AssetType;
import com.finrisk.radar.risk.RiskCalculationJob;
import com.finrisk.radar.risk.kafka.RiskEventPublisher;
import com.finrisk.radar.user.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RiskCalculationRequestServiceTest {
  @Test
  void requestsCollectionBeforeCalculationWhenRequiredDataIsMissing() {
    UserRepository users = mock(UserRepository.class);
    AssetRepository assets = mock(AssetRepository.class);
    RiskCalculationJobService jobs = mock(RiskCalculationJobService.class);
    RiskEventPublisher publisher = mock(RiskEventPublisher.class);
    RiskDataPreparationService preparation = mock(RiskDataPreparationService.class);
    Asset asset = mock(Asset.class);
    RiskCalculationJob job =
        RiskCalculationJob.collecting(1L, 2L, "corporate-risk-v1", LocalDate.now());
    when(users.existsById(1L)).thenReturn(true);
    when(assets.findById(2L)).thenReturn(Optional.of(asset));
    when(asset.getAssetType()).thenReturn(AssetType.BOND_ISSUER);
    when(preparation.hasRequiredData(asset, LocalDate.now())).thenReturn(false);
    when(jobs.create(1L, 2L, "corporate-risk-v1", true)).thenReturn(job);
    RiskCalculationRequestService service =
        new RiskCalculationRequestService(users, assets, jobs, publisher, preparation);

    service.request(1L, 2L);

    verify(preparation).requestCollection(job, asset);
    verifyNoInteractions(publisher);
  }
}
