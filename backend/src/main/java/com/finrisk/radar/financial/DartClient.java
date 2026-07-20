package com.finrisk.radar.financial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@EnableConfigurationProperties(DartProperties.class)
public class DartClient {
  private final DartProperties properties;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public DartClient(
      DartProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(15));
    this.restClient =
        builder
            .requestFactory(requestFactory)
            .baseUrl(
                properties.baseUrl() == null || properties.baseUrl().isBlank()
                    ? "https://opendart.fss.or.kr"
                    : properties.baseUrl())
            .build();
  }

  public String downloadCorpCodeXml() {
    requireKey();
    try {
      byte[] body =
          restClient
              .get()
              .uri(
                  uri ->
                      uri.path("/api/corpCode.xml")
                          .queryParam("crtfc_key", properties.apiKey())
                          .build())
              .retrieve()
              .body(byte[].class);
      return unzipXml(body);
    } catch (BusinessException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new DartClientException("DART corp code request failed.", exception);
    }
  }

  public RawDartFinancialStatement fetchFinancialStatement(
      String corpCode, Integer year, Integer quarter) {
    return fetchLatestFinancialStatement(corpCode, year, quarter, 0);
  }

  public RawDartFinancialStatement fetchLatestFinancialStatement(
      String corpCode, Integer year, Integer quarter, int previousPeriodLimit) {
    int candidateYear = year;
    int candidateQuarter = quarter;
    for (int attempt = 0; attempt <= previousPeriodLimit; attempt++) {
      RawDartFinancialStatement cfs =
          fetch(corpCode, candidateYear, candidateQuarter, DartStatementDivision.CFS, false);
      if (hasRows(cfs.payload())) return cfs;
      RawDartFinancialStatement ofs =
          fetch(corpCode, candidateYear, candidateQuarter, DartStatementDivision.OFS, true);
      if (hasRows(ofs.payload())) return ofs;
      if (candidateQuarter == 1) {
        candidateYear--;
        candidateQuarter = 4;
      } else {
        candidateQuarter--;
      }
    }
    throw new DartClientException("DART financial statement returned no rows for recent periods.");
  }

  public String searchDisclosures(
      String corpCode, java.time.LocalDate from, java.time.LocalDate to, int page) {
    requireKey();
    try {
      return restClient
          .get()
          .uri(
              uri ->
                  uri.path("/api/list.json")
                      .queryParam("crtfc_key", properties.apiKey())
                      .queryParam("corp_code", corpCode)
                      .queryParam(
                          "bgn_de", from.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
                      .queryParam(
                          "end_de", to.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
                      .queryParam("page_no", page)
                      .queryParam("page_count", 100)
                      .build())
          .retrieve()
          .body(String.class);
    } catch (RestClientException exception) {
      throw new DartClientException("DART disclosure search failed.", exception);
    }
  }

  public byte[] downloadDisclosureDocument(String receiptNumber) {
    requireKey();
    try {
      return restClient
          .get()
          .uri(
              uri ->
                  uri.path("/api/document.xml")
                      .queryParam("crtfc_key", properties.apiKey())
                      .queryParam("rcept_no", receiptNumber)
                      .build())
          .retrieve()
          .body(byte[].class);
    } catch (RestClientException exception) {
      throw new DartClientException("DART disclosure document request failed.", exception);
    }
  }

  private RawDartFinancialStatement fetch(
      String corpCode,
      Integer year,
      Integer quarter,
      DartStatementDivision division,
      boolean fallbackUsed) {
    requireKey();
    try {
      String payload =
          restClient
              .get()
              .uri(
                  uri ->
                      uri.path("/api/fnlttSinglAcntAll.json")
                          .queryParam("crtfc_key", properties.apiKey())
                          .queryParam("corp_code", corpCode)
                          .queryParam("bsns_year", year)
                          .queryParam("reprt_code", DartReportCode.fromQuarter(quarter))
                          .queryParam("fs_div", division.name())
                          .build())
              .retrieve()
              .body(String.class);
      return new RawDartFinancialStatement(
          corpCode, year, quarter, division, fallbackUsed, payload);
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
      return ("000".equals(status.asText()) || status.isMissingNode())
          && list.isArray()
          && !list.isEmpty();
    } catch (Exception exception) {
      throw new DartClientException("DART financial statement response is malformed.", exception);
    }
  }

  private void requireKey() {
    if (!properties.configured()) throw new BusinessException(ErrorCode.DART_API_KEY_MISSING);
  }

  private String unzipXml(byte[] body) {
    if (body == null || body.length == 0)
      throw new DartClientException("DART corp code response was empty.");
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
