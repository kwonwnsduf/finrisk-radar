package com.finrisk.radar.collector.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finrisk.radar.collector.client.RawMarketData;
import com.finrisk.radar.marketprice.MarketPriceSource;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.time.LocalDate;

public class S3RawMarketDataStorage implements RawMarketDataStorage {
	private final S3Client client;
	private final ObjectMapper mapper;
	private final String bucket;
	public S3RawMarketDataStorage(S3Client client, ObjectMapper mapper, String bucket) {
		this.client = client; this.mapper = mapper; this.bucket = bucket;
	}
	@Override
	public String store(Long assetId, String ticker, LocalDate startDate, LocalDate endDate, RawMarketData raw) {
		try {
			if (!ticker.matches("[A-Z0-9._-]+")) throw new RawStorageException("Ticker is not safe for an S3 key.");
			String key = "market-prices/%s/%d/%s_%s.json".formatted(ticker, startDate.getYear(), startDate, endDate);
			ObjectNode envelope = mapper.createObjectNode();
			envelope.put("assetId", assetId); envelope.put("ticker", ticker); envelope.put("source", raw.source().name());
			envelope.put("startDate", startDate.toString()); envelope.put("endDate", endDate.toString());
			envelope.put("fetchedAt", Instant.now().toString());
			if (raw.source() == MarketPriceSource.YAHOO) envelope.set("payload", mapper.readTree(raw.payload()));
			else envelope.put("payload", raw.payload());
			byte[] body = mapper.writeValueAsBytes(envelope);
			client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/json").build(),
					RequestBody.fromBytes(body));
			return "s3://" + bucket + "/" + key;
		} catch (Exception exception) {
			throw new RawStorageException("Raw market data could not be stored.", exception);
		}
	}
}
