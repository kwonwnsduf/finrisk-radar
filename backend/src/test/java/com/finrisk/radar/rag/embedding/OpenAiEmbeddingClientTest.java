package com.finrisk.radar.rag.embedding;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiEmbeddingClientTest {
  @Test
  void mapsBatchResponseByIndexAndValidatesDimensions() throws Exception {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(properties("secret"), builder.build());
    List<Float> first = Collections.nCopies(1536, 0.1f);
    List<Float> second = Collections.nCopies(1536, 0.2f);
    String response =
        new ObjectMapper()
            .writeValueAsString(
                Map.of(
                    "model",
                    "text-embedding-3-small",
                    "data",
                    List.of(
                        Map.of("index", 1, "embedding", second),
                        Map.of("index", 0, "embedding", first))));
    server
        .expect(requestTo("https://api.openai.test/v1/embeddings"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"dimensions\":1536")))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    List<float[]> result = client.embedAll(List.of("first", "second"));

    assertThat(result).hasSize(2);
    assertThat(result.get(0)).hasSize(1536);
    assertThat(result.get(0)[0]).isEqualTo(0.1f);
    assertThat(result.get(1)[0]).isEqualTo(0.2f);
    server.verify();
  }

  @Test
  void classifiesAuthenticationFailureAsNonRetryable() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(properties("secret"), builder.build());
    server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> client.embed("query"))
        .isInstanceOfSatisfying(
            EmbeddingClientException.class,
            error -> {
              assertThat(error.getCode()).isEqualTo("OPENAI_HTTP_401");
              assertThat(error.isRetryable()).isFalse();
              assertThat(error.getMessage()).doesNotContain("secret");
            });
  }

  @Test
  void rejectsNon1536Configuration() {
    assertThatThrownBy(
            () ->
                new OpenAiEmbeddingProperties(
                    "https://api.openai.test",
                    "secret",
                    "model",
                    768,
                    32,
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private OpenAiEmbeddingProperties properties(String key) {
    return new OpenAiEmbeddingProperties(
        "https://api.openai.test",
        key,
        "text-embedding-3-small",
        1536,
        32,
        Duration.ofSeconds(1),
        Duration.ofSeconds(1));
  }
}
