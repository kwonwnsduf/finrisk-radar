package com.finrisk.radar.document;

import static org.mockito.Mockito.*;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.document.collector.*;
import com.finrisk.radar.document.service.*;
import com.finrisk.radar.financial.DartCorpCodeService;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class DocumentCollectionExecutionServiceTest {
  @Test
  void usesDirectAssetCorpCodeMappingForOpenDartCollection() {
    DocumentCollectionJobService jobs = mock(DocumentCollectionJobService.class);
    AssetRepository assets = mock(AssetRepository.class);
    DartCorpCodeService corpCodes = mock(DartCorpCodeService.class);
    DocumentCollectorRegistry registry = mock(DocumentCollectorRegistry.class);
    DocumentPersistenceService persistence = mock(DocumentPersistenceService.class);
    DocumentAssetMappingService mapping = mock(DocumentAssetMappingService.class);
    DocumentSourceCollector collector = mock(DocumentSourceCollector.class);
    Asset asset =
        Asset.create(
            "JTBC", "JTBC", null, "Media", "KR", "KRW", AssetType.BOND_ISSUER);
    asset.assignDartCorpCode("00922702");
    DocumentCollectionJob job =
        DocumentCollectionJob.requested(
            1L,
            10L,
            DocumentSourceType.OPEN_DART,
            LocalDate.of(2026, 7, 19),
            LocalDate.of(2026, 7, 20));
    when(jobs.start(job.getJobId())).thenReturn(true);
    when(jobs.get(job.getJobId())).thenReturn(job);
    when(assets.findById(10L)).thenReturn(Optional.of(asset));
    when(corpCodes.findCorpCode(asset)).thenReturn("00922702");
    when(registry.get(DocumentSourceType.OPEN_DART)).thenReturn(collector);
    when(collector.collect(any())).thenReturn(List.of());
    DocumentCollectionExecutionService service =
        new DocumentCollectionExecutionService(
            jobs, assets, corpCodes, registry, persistence, mapping);

    service.execute(job.getJobId());

    verify(corpCodes).findCorpCode(asset);
    verify(corpCodes, never()).findCorpCodeByStockCode(anyString());
    verify(collector)
        .collect(
            argThat(
                context ->
                    context.asset() == asset && "00922702".equals(context.corpCode())));
    verify(jobs).complete(job.getJobId(), 0);
  }
}
