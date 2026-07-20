package com.finrisk.radar.asset;

import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.seed-assets.enabled", havingValue = "true")
public class AssetSeedData implements CommandLineRunner {
  private static final String LEGACY_MARKET = "LEGACY";

  private final AssetRepository assetRepository;

  public AssetSeedData(AssetRepository assetRepository) {
    this.assetRepository = assetRepository;
  }

  @Override
  @Transactional
  public void run(String... args) {
    for (Seed seed : seeds()) {
      if (assetRepository.existsByTickerAndMarket(seed.ticker(), seed.market())) continue;
      assetRepository
          .findByTickerAndMarket(seed.ticker(), LEGACY_MARKET)
          .ifPresentOrElse(
              legacy ->
                  legacy.canonicalize(
                      seed.name(),
                      seed.market(),
                      seed.sector(),
                      seed.country(),
                      seed.currency(),
                      seed.assetType()),
              () -> assetRepository.save(seed.toAsset()));
    }
    assetRepository
        .findByTickerAndMarket("JTBC", "PRIVATE")
        .ifPresent(asset -> asset.assignDartCorpCode("00922702"));
  }

  private static List<Seed> seeds() {
    return List.of(
        new Seed("삼성전자", "005930", "KOSPI", "Semiconductor", "KR", "KRW", AssetType.STOCK),
        new Seed("NAVER", "035420", "KOSPI", "Internet", "KR", "KRW", AssetType.STOCK),
        new Seed("카카오", "035720", "KOSPI", "Internet", "KR", "KRW", AssetType.STOCK),
        new Seed("제이알글로벌리츠", "348950", "KOSPI", "REIT", "KR", "KRW", AssetType.REIT),
        new Seed("맥쿼리인프라", "088980", "KOSPI", "Infrastructure", "KR", "KRW", AssetType.REIT),
        new Seed("Apple", "AAPL", "NASDAQ", "Technology", "US", "USD", AssetType.STOCK),
        new Seed("NVIDIA", "NVDA", "NASDAQ", "Semiconductor", "US", "USD", AssetType.STOCK),
        new Seed("JTBC", "JTBC", "PRIVATE", "Media", "KR", "KRW", AssetType.BOND_ISSUER),
        new Seed("콘텐트리중앙", "036420", "KOSPI", "Media", "KR", "KRW", AssetType.BOND_ISSUER));
  }

  private record Seed(
      String name,
      String ticker,
      String market,
      String sector,
      String country,
      String currency,
      AssetType assetType) {
    Asset toAsset() {
      return Asset.create(name, ticker, market, sector, country, currency, assetType);
    }
  }
}
