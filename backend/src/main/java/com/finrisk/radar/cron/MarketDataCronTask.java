package com.finrisk.radar.cron;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.asset.AssetType;
import com.finrisk.radar.collector.api.MarketPriceFetchRequest;
import com.finrisk.radar.collector.service.CollectionRequestService;
import com.finrisk.radar.collector.service.MarketTickerResolver;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MarketDataCronTask implements Day20CronTask {
  private static final Logger log = LoggerFactory.getLogger(MarketDataCronTask.class);
  private final AssetRepository assets;
  private final UserRepository users;
  private final MarketTickerResolver tickers;
  private final CollectionRequestService requests;
  private final String systemUserEmail;
  private final int lookbackDays;
  private final Clock clock;

  @Autowired
  public MarketDataCronTask(
      AssetRepository assets,
      UserRepository users,
      MarketTickerResolver tickers,
      CollectionRequestService requests,
      @Value("${app.cron.system-user-email:}") String systemUserEmail,
      @Value("${app.cron.market-data-lookback-days:10}") int lookbackDays) {
    this(
        assets,
        users,
        tickers,
        requests,
        systemUserEmail,
        lookbackDays,
        Clock.system(ZoneId.of("Asia/Seoul")));
  }

  MarketDataCronTask(
      AssetRepository assets,
      UserRepository users,
      MarketTickerResolver tickers,
      CollectionRequestService requests,
      String systemUserEmail,
      int lookbackDays,
      Clock clock) {
    this.assets = assets;
    this.users = users;
    this.tickers = tickers;
    this.requests = requests;
    this.systemUserEmail = systemUserEmail;
    this.lookbackDays = lookbackDays;
    this.clock = clock;
  }

  @Override
  public String name() {
    return "market-data";
  }

  @Override
  public void run() {
    if (systemUserEmail == null || systemUserEmail.isBlank()) {
      throw new IllegalStateException("CRON_SYSTEM_USER_EMAIL is required.");
    }
    if (lookbackDays < 1) throw new IllegalStateException("Market data lookback must be positive.");
    User user =
        users.findByEmail(systemUserEmail.trim())
            .orElseThrow(() -> new IllegalStateException("Cron system user was not found."));
    LocalDate endDate = LocalDate.now(clock);
    LocalDate startDate = endDate.minusDays(lookbackDays);
    int requested = 0;
    int failed = 0;
    for (Asset asset : assets.findAllByOrderByNameAsc()) {
      if (asset.getAssetType() != AssetType.STOCK && asset.getAssetType() != AssetType.REIT) continue;
      try {
        String ticker = tickers.resolve(asset);
        requests.request(
            user.getId(), new MarketPriceFetchRequest(asset.getId(), ticker, startDate, endDate));
        requested++;
      } catch (RuntimeException exception) {
        failed++;
        log.error(
            "Market data cron request failed: assetId={}, ticker={}",
            asset.getId(),
            asset.getTicker(),
            exception);
      }
    }
    log.info("event=market_data_cron_summary requested={} failed={}", requested, failed);
    if (failed > 0) throw new IllegalStateException("Market data collection failed for " + failed + " asset(s).");
  }
}
