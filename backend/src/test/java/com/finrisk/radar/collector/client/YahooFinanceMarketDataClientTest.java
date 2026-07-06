package com.finrisk.radar.collector.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.marketprice.MarketPriceSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YahooFinanceMarketDataClientTest {
	private static final String RESPONSE = """
			{"chart":{"result":[{"meta":{"exchangeTimezoneName":"Asia/Seoul"},
			"timestamp":[1704153600],"indicators":{"quote":[{"open":[78200.0],
			"high":[79800.0],"low":[78200.0],"close":[79600.0],"volume":[17142847]}]}}],"error":null}}
			""";

	@Test
	void sendsBrowserHeadersAndReturnsYahooRawData() {
		RestClient.Builder builder = YahooFinanceMarketDataClient.configuredBuilder(
				RestClient.builder(), "https://query1.finance.yahoo.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		YahooFinanceMarketDataClient client = new YahooFinanceMarketDataClient(builder.build(), new ObjectMapper());
		server.expect(requestTo(org.hamcrest.Matchers.containsString("/v8/finance/chart/005930.KS")))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.USER_AGENT, org.hamcrest.Matchers.containsString("Mozilla/5.0")))
				.andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
				.andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

		RawMarketData result = client.fetch("005930.KS", LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-04"));

		assertThat(result.source()).isEqualTo(MarketPriceSource.YAHOO);
		assertThat(result.payload()).contains("79600.0");
		server.verify();
	}

	@Test
	void retriesRateLimitBeforeReturningYahooData() {
		RestClient.Builder builder = YahooFinanceMarketDataClient.configuredBuilder(
				RestClient.builder(), "https://query1.finance.yahoo.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		YahooFinanceMarketDataClient client = new YahooFinanceMarketDataClient(builder.build(), new ObjectMapper());
		server.expect(requestTo(org.hamcrest.Matchers.containsString("005930.KS")))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		server.expect(requestTo(org.hamcrest.Matchers.containsString("005930.KS")))
				.andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

		assertThat(client.fetch("005930.KS", LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-04")).source())
				.isEqualTo(MarketPriceSource.YAHOO);
		server.verify();
	}
}
