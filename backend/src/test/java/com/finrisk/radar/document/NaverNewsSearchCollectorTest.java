package com.finrisk.radar.document.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.asset.*;
import com.finrisk.radar.document.DocumentContentScope;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverNewsSearchCollectorTest {
  private static final String RESPONSE =
      """
      {"lastBuildDate":"Mon, 13 Jul 2026 20:50:00 +0900","total":1,"start":1,"display":1,
       "items":[{"title":"<b>Samsung</b> refinancing news",
       "originallink":"https://example.com/news/1","link":"https://n.news.naver.com/1",
       "description":"The company reported <b>USD 100M</b> refinancing.",
       "pubDate":"Mon, 13 Jul 2026 20:50:00 +0900"}]}
      """;

  @Test
  void usesNaverApiHubContractAndDoesNotCrawlPublisherUrl() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    NaverNewsSearchCollector collector =
        new NaverNewsSearchCollector(
            new NaverNewsProperties(
                "https://naverapihub.apigw.ntruss.com", "client-id", "client-secret"),
            new ObjectMapper(),
            builder.baseUrl("https://naverapihub.apigw.ntruss.com").build());
    server
        .expect(
            requestTo(
                org.hamcrest.Matchers.allOf(
                    org.hamcrest.Matchers.containsString(
                        "https://naverapihub.apigw.ntruss.com/search/v1/news?"),
                    org.hamcrest.Matchers.containsString("display=100"),
                    org.hamcrest.Matchers.containsString("start=1"),
                    org.hamcrest.Matchers.containsString("sort=date"),
                    org.hamcrest.Matchers.containsString("format=json"))))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("X-NCP-APIGW-API-KEY-ID", "client-id"))
        .andExpect(header("X-NCP-APIGW-API-KEY", "client-secret"))
        .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

    Asset asset =
        Asset.create("Samsung", "005930", "KRX", "Technology", "KR", "KRW", AssetType.BOND_ISSUER);
    var result =
        collector.collect(
            new DocumentCollectionContext(
                UUID.randomUUID(),
                asset,
                null,
                LocalDate.parse("2026-07-13"),
                LocalDate.parse("2026-07-13")));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).title()).isEqualTo("Samsung refinancing news");
    assertThat(result.get(0).content())
        .isEqualTo("Samsung refinancing news. The company reported USD 100M refinancing.");
    assertThat(result.get(0).sourceUrl()).isEqualTo("https://example.com/news/1");
    assertThat(result.get(0).contentScope()).isEqualTo(DocumentContentScope.SNIPPET);
    server.verify();
  }
}
