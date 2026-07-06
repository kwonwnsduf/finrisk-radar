package com.finrisk.radar.marketprice;

import com.finrisk.radar.collector.service.PriceBar;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MarketPriceWriter {
	private static final String UPSERT_SQL = """
			INSERT INTO market_prices
			(asset_id, date, open, high, low, close, volume, source, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (asset_id, date, source) DO UPDATE SET
			open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low,
			close = EXCLUDED.close, volume = EXCLUDED.volume, updated_at = EXCLUDED.updated_at
			""";

	private final JdbcTemplate jdbcTemplate;

	public MarketPriceWriter(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public void upsert(Long assetId, MarketPriceSource source, List<PriceBar> bars) {
		LocalDateTime now = LocalDateTime.now();
		jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement statement, int i) throws SQLException {
				PriceBar bar = bars.get(i);
				statement.setLong(1, assetId);
				statement.setObject(2, bar.date());
				statement.setBigDecimal(3, bar.open());
				statement.setBigDecimal(4, bar.high());
				statement.setBigDecimal(5, bar.low());
				statement.setBigDecimal(6, bar.close());
				statement.setLong(7, bar.volume());
				statement.setString(8, source.name());
				statement.setTimestamp(9, Timestamp.valueOf(now));
				statement.setTimestamp(10, Timestamp.valueOf(now));
			}

			@Override
			public int getBatchSize() { return bars.size(); }
		});
	}
}
