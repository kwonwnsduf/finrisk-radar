package com.finrisk.radar.cron;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.collector.service.CollectionRequestService;
import com.finrisk.radar.collector.service.MarketTickerResolver;
import com.finrisk.radar.user.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MarketDataCronTaskTest {
  @Test
  void requestsOnlyStockAndReitWithTenDayLookback() {
    AssetRepository assets = mock(AssetRepository.class);
    UserRepository users = mock(UserRepository.class);
    CollectionRequestService requests = mock(CollectionRequestService.class);
    User user = mock(User.class);
    when(user.getId()).thenReturn(7L);
    when(users.findByEmail("cron@example.com")).thenReturn(Optional.of(user));
    Asset stock = asset("Samsung", "005930", "KOSPI", AssetType.STOCK);
    Asset reit = asset("Reit", "330590", "KOSPI", AssetType.REIT);
    Asset bond = asset("Issuer", "ISSUER", "PRIVATE", AssetType.BOND_ISSUER);
    when(assets.findAllByOrderByNameAsc()).thenReturn(List.of(stock, reit, bond));
    Clock clock = Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);

    new MarketDataCronTask(
            assets,
            users,
            new MarketTickerResolver(),
            requests,
            "cron@example.com",
            10,
            clock)
        .run();

    verify(requests, times(2))
        .request(
            eq(7L),
            argThat(
                request ->
                    request.startDate().equals(LocalDate.of(2026, 7, 26))
                        && request.endDate().equals(LocalDate.of(2026, 8, 5))));
  }

  @Test
  void requiresConfiguredSystemUser() {
    MarketDataCronTask task =
        new MarketDataCronTask(
            mock(AssetRepository.class),
            mock(UserRepository.class),
            mock(MarketTickerResolver.class),
            mock(CollectionRequestService.class),
            " ",
            10,
            Clock.systemUTC());

    assertThatThrownBy(task::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CRON_SYSTEM_USER_EMAIL");
  }

  private Asset asset(String name, String ticker, String market, AssetType type) {
    return Asset.create(name, ticker, market, null, "KR", "KRW", type);
  }
}
