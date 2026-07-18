package com.finrisk.radar.document.collector;

import com.fasterxml.jackson.databind.*;
import com.finrisk.radar.document.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.jsoup.Jsoup;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@EnableConfigurationProperties(NaverNewsProperties.class)
public class NaverNewsSearchCollector implements DocumentSourceCollector {
  private final NaverNewsProperties properties;
  private final ObjectMapper mapper;
  private final RestClient client;

  @org.springframework.beans.factory.annotation.Autowired
  public NaverNewsSearchCollector(
      NaverNewsProperties p, ObjectMapper mapper, RestClient.Builder builder) {
    this(p, mapper, createClient(p, builder));
  }

  NaverNewsSearchCollector(NaverNewsProperties p, ObjectMapper mapper, RestClient client) {
    properties = p;
    this.mapper = mapper;
    this.client = client;
  }

  private static RestClient createClient(NaverNewsProperties p, RestClient.Builder builder) {
    SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
    f.setConnectTimeout(Duration.ofSeconds(5));
    f.setReadTimeout(Duration.ofSeconds(15));
    return builder
        .requestFactory(f)
        .baseUrl(
            p.baseUrl() == null || p.baseUrl().isBlank()
                ? "https://naverapihub.apigw.ntruss.com"
                : p.baseUrl())
        .build();
  }

  public boolean supports(DocumentSourceType source) {
    return source == DocumentSourceType.NAVER_NEWS;
  }

  public List<CollectedDocument> collect(DocumentCollectionContext context) {
    if (!properties.configured())
      throw new IllegalStateException("Naver News API credentials are not configured.");
    String query = context.asset().getName() + " " + context.asset().getTicker();
    String raw =
        client
            .get()
            .uri(
                u ->
                    u.path("/search/v1/news")
                        .queryParam("query", query)
                        .queryParam("display", 100)
                        .queryParam("start", 1)
                        .queryParam("sort", "date")
                        .queryParam("format", "json")
                        .build())
            .header("X-NCP-APIGW-API-KEY-ID", properties.clientId())
            .header("X-NCP-APIGW-API-KEY", properties.clientSecret())
            .retrieve()
            .body(String.class);
    try {
      JsonNode root = mapper.readTree(raw);
      List<CollectedDocument> result = new ArrayList<>();
      for (JsonNode item : root.path("items")) {
        LocalDateTime published = parseDate(item.path("pubDate").asText());
        if (published != null
            && (published.toLocalDate().isBefore(context.fromDate())
                || published.toLocalDate().isAfter(context.toDate()))) continue;
        String title = clean(item.path("title").asText());
        String description = clean(item.path("description").asText());
        String original = item.path("originallink").asText();
        String link = original.isBlank() ? item.path("link").asText() : original;
        result.add(
            new CollectedDocument(
                DocumentType.NEWS,
                DocumentSourceType.NAVER_NEWS,
                host(link),
                title,
                title + ". " + description,
                description,
                link,
                link,
                published,
                mapper.writeValueAsBytes(item),
                "application/json",
                "json",
                null,
                null,
                null,
                null));
      }
      return result;
    } catch (Exception e) {
      throw new IllegalStateException("Naver News response is malformed.", e);
    }
  }

  private static String clean(String value) {
    return Jsoup.parse(value == null ? "" : value).text().trim();
  }

  private static String host(String url) {
    try {
      return java.net.URI.create(url).getHost();
    } catch (Exception e) {
      return "Naver News";
    }
  }

  private static LocalDateTime parseDate(String value) {
    try {
      return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
          .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
          .toLocalDateTime();
    } catch (Exception e) {
      return null;
    }
  }
}
