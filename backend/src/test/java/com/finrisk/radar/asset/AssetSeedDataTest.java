package com.finrisk.radar.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssetSeedDataTest {
  @Test
  void assignsTheOfficialDartCorpCodeToJtbcOnAnewDatabase() throws Exception {
    AssetRepository repository = mock(AssetRepository.class);
    Asset jtbc = Asset.create("JTBC", "JTBC", "PRIVATE", "Media", "KR", "KRW", AssetType.BOND_ISSUER);
    when(repository.findByTickerAndMarket(any(), eq("LEGACY"))).thenReturn(Optional.empty());
    when(repository.findByTickerAndMarket("JTBC", "PRIVATE")).thenReturn(Optional.of(jtbc));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    new AssetSeedData(repository).run();

    assertThat(jtbc.getDartCorpCode()).isEqualTo("00922702");
  }
}
