package com.finrisk.radar.collector.log;

import com.finrisk.radar.marketprice.MarketPriceSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionLogTest {
	@Test void tracksTheCollectionLifecycle() {
		CollectionLog log = CollectionLog.requested(1L, 2L, "005930.KS",
				LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"));
		assertThat(log.getStatus()).isEqualTo(CollectionStatus.REQUESTED);
		assertThat(log.start()).isTrue();
		log.rawStored(MarketPriceSource.YAHOO, "s3://bucket/key.json");
		log.complete(MarketPriceSource.YAHOO, 250);
		assertThat(log.getStatus()).isEqualTo(CollectionStatus.COMPLETED);
		assertThat(log.getRawS3Path()).isEqualTo("s3://bucket/key.json");
		assertThat(log.getCompletedAt()).isNotNull();
	}

	@Test void duplicateStartIsIgnored() {
		CollectionLog log = CollectionLog.requested(1L, 2L, "AAPL",
				LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"));
		assertThat(log.start()).isTrue();
		assertThat(log.start()).isFalse();
	}
}
