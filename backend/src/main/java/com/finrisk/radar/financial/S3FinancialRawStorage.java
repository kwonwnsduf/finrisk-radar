package com.finrisk.radar.financial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finrisk.radar.collector.storage.RawStorageException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class S3FinancialRawStorage implements FinancialRawStorage {
	private final S3Client client;
	private final ObjectMapper mapper;
	private final String bucket;

	public S3FinancialRawStorage(S3Client client, ObjectMapper mapper, String bucket) {
		this.client = client; this.mapper = mapper; this.bucket = bucket;
	}

	@Override
	public String storeFinancialStatement(Long assetId, String stockCode, String corpCode, RawDartFinancialStatement raw) {
		try {
			String key = "financial-statements/%d/%d/%d/raw.json".formatted(assetId, raw.year(), raw.quarter());
			ObjectNode envelope = mapper.createObjectNode();
			envelope.put("assetId", assetId);
			envelope.put("stockCode", stockCode);
			envelope.put("corpCode", corpCode);
			envelope.put("year", raw.year());
			envelope.put("quarter", raw.quarter());
			envelope.put("statementDivision", raw.division().name());
			envelope.put("fallbackUsed", raw.fallbackUsed());
			envelope.put("fetchedAt", Instant.now().toString());
			envelope.set("payload", mapper.readTree(raw.payload()));
			put(key, "application/json", mapper.writeValueAsBytes(envelope));
			return "s3://" + bucket + "/" + key;
		} catch (Exception exception) {
			throw new RawStorageException("Raw DART financial data could not be stored.", exception);
		}
	}

	@Override
	public String storeCorpCodesXml(String xml) {
		String key = "dart-corp-codes/corp_codes.xml";
		put(key, "application/xml", xml.getBytes(StandardCharsets.UTF_8));
		return "s3://" + bucket + "/" + key;
	}

	@Override
	public String storeDebtMaturitySample(String csv) {
		String key = "debt-maturity/sample/debt_maturity_sample.csv";
		put(key, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
		return "s3://" + bucket + "/" + key;
	}

	private void put(String key, String contentType, byte[] body) {
		try {
			client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
					RequestBody.fromBytes(body));
		} catch (Exception exception) {
			throw new RawStorageException("Raw financial data could not be stored.", exception);
		}
	}
}
