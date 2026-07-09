package com.finrisk.radar.financial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@EnableConfigurationProperties(DartProperties.class)
public class DartClient {
	private final DartProperties properties;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public DartClient(DartProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
		this.properties = properties; this.objectMapper = objectMapper;
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		requestFactory.setReadTimeout(Duration.ofSeconds(15));
		this.restClient = builder.requestFactory(requestFactory)
				.baseUrl(properties.baseUrl() == null || properties.baseUrl().isBlank()
						? "https://opendart.fss.or.kr" : properties.baseUrl())
				.build();
	}

	public String downloadCorpCodeXml() {
		requireKey();
		try {
			byte[] body = restClient.get()
					.uri(uri -> uri.path("/api/corpCode.xml").queryParam("crtfc_key", properties.apiKey()).build())
					.retrieve()
					.body(byte[].class);
			return unzipXml(body);
		} catch (BusinessException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new DartClientException("DART corp code request failed.", exception);
		}
	}

	public RawDartFinancialStatement fetchFinancialStatement(String corpCode, Integer year, Integer quarter) {
		RawDartFinancialStatement cfs = fetch(corpCode, year, quarter, DartStatementDivision.CFS, false);
		if (hasRows(cfs.payload())) return cfs;
		RawDartFinancialStatement ofs = fetch(corpCode, year, quarter, DartStatementDivision.OFS, true);
		if (!hasRows(ofs.payload())) throw new DartClientException("DART financial statement returned no rows.");
		return ofs;
	}

	private RawDartFinancialStatement fetch(String corpCode, Integer year, Integer quarter,
			DartStatementDivision division, boolean fallbackUsed) {
		requireKey();
		try {
			String payload = restClient.get()
					.uri(uri -> uri.path("/api/fnlttSinglAcntAll.json")
							.queryParam("crtfc_key", properties.apiKey())
							.queryParam("corp_code", corpCode)
							.queryParam("bsns_year", year)
							.queryParam("reprt_code", DartReportCode.fromQuarter(quarter))
							.queryParam("fs_div", division.name())
							.build())
					.retrieve()
					.body(String.class);
			return new RawDartFinancialStatement(corpCode, year, quarter, division, fallbackUsed, payload);
		} catch (BusinessException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new DartClientException("DART financial statement request failed.", exception);
		}
	}

	private boolean hasRows(String payload) {
		try {
			JsonNode root = objectMapper.readTree(payload);
			JsonNode status = root.path("status");
			JsonNode list = root.path("list");
			return ("000".equals(status.asText()) || status.isMissingNode()) && list.isArray() && !list.isEmpty();
		} catch (Exception exception) {
			throw new DartClientException("DART financial statement response is malformed.", exception);
		}
	}

	private void requireKey() {
		if (!properties.configured()) throw new BusinessException(ErrorCode.DART_API_KEY_MISSING);
	}

	private String unzipXml(byte[] body) {
		if (body == null || body.length == 0) throw new DartClientException("DART corp code response was empty.");
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".xml")) {
					return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
			throw new DartClientException("DART corp code ZIP did not contain XML.");
		} catch (DartClientException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new DartClientException("DART corp code ZIP could not be read.", exception);
		}
	}
}
