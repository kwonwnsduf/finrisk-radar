package com.finrisk.radar.cron;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.finrisk.radar.document.service.DocumentCollectionBatchService;
import org.junit.jupiter.api.Test;

class DocumentCollectionCronTaskTest {
  @Test
  void failsJobWhenAnyAssetRequestFailed() {
    DocumentCollectionBatchService batch = mock(DocumentCollectionBatchService.class);
    when(batch.collect()).thenReturn(new DocumentCollectionBatchService.Result(2, 1));

    assertThatThrownBy(() -> new DocumentCollectionCronTask(batch).run())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("1 asset");
  }
}
