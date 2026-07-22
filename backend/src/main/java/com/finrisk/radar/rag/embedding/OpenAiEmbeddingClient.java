package com.finrisk.radar.rag.embedding;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
@EnableConfigurationProperties(OpenAiEmbeddingProperties.class)
public class OpenAiEmbeddingClient implements EmbeddingClient {
  private final OpenAiEmbeddingProperties properties;
  private final RestClient client;

  @Autowired
  public OpenAiEmbeddingClient(OpenAiEmbeddingProperties properties, RestClient.Builder builder) {
    this.properties = properties;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    this.client =
        builder
            .requestFactory(factory)
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .build();
  }

  OpenAiEmbeddingClient(OpenAiEmbeddingProperties properties, RestClient client) {
    this.properties = properties;
    this.client = client;
  }

  @Override
  public float[] embed(String text) {
    return embedAll(List.of(text)).get(0);
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    if (!properties.configured()) {
      throw new EmbeddingClientException(
          "OPENAI_API_KEY_MISSING", "OpenAI API key is not configured.", false);
    }
    if (texts == null
        || texts.isEmpty()
        || texts.stream().anyMatch(t -> t == null || t.isBlank())) {
      throw new EmbeddingClientException(
          "EMBEDDING_INPUT_INVALID", "Embedding input must not be empty.", false);
    }
    try {
      Response response =
          client
              .post()
              .uri("/v1/embeddings")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
              .body(new Request(texts, properties.model(), properties.dimensions(), "float"))
              .retrieve()
              .body(Response.class);
      return validate(response, texts.size());
    } catch (EmbeddingClientException exception) {
      throw exception;
    } catch (RestClientResponseException exception) {
      int status = exception.getStatusCode().value();
      boolean retryable = status == 429 || exception.getStatusCode().is5xxServerError();
      throw new EmbeddingClientException(
          "OPENAI_HTTP_" + status,
          "OpenAI embedding request failed with HTTP " + status + ".",
          retryable,
          exception);
    } catch (ResourceAccessException exception) {
      throw new EmbeddingClientException(
          "OPENAI_NETWORK_ERROR", "OpenAI embedding request failed.", true, exception);
    } catch (RestClientException exception) {
      throw new EmbeddingClientException(
          "OPENAI_CLIENT_ERROR",
          "OpenAI embedding response could not be processed.",
          false,
          exception);
    }
  }

  private List<float[]> validate(Response response, int expected) {
    if (response == null || response.data() == null || response.data().size() != expected) {
      throw new EmbeddingClientException(
          "EMBEDDING_COUNT_MISMATCH", "Embedding response count did not match input count.", false);
    }
    Data[] ordered = new Data[expected];
    for (Data item : response.data()) {
      if (item.index() < 0 || item.index() >= expected || ordered[item.index()] != null) {
        throw new EmbeddingClientException(
            "EMBEDDING_INDEX_INVALID", "Embedding response index was invalid.", false);
      }
      if (item.embedding() == null || item.embedding().length != properties.dimensions()) {
        throw new EmbeddingClientException(
            "EMBEDDING_DIMENSION_MISMATCH", "Embedding response dimension was invalid.", false);
      }
      ordered[item.index()] = item;
    }
    if (Arrays.stream(ordered).anyMatch(Objects::isNull)) {
      throw new EmbeddingClientException(
          "EMBEDDING_INDEX_INVALID", "Embedding response index was incomplete.", false);
    }
    return Arrays.stream(ordered).map(Data::embedding).toList();
  }

  @Override
  public String modelName() {
    return properties.model();
  }

  @Override
  public int dimensions() {
    return properties.dimensions();
  }

  public int batchSize() {
    return properties.batchSize();
  }

  record Request(List<String> input, String model, int dimensions, String encoding_format) {}

  record Response(List<Data> data, String model) {}

  record Data(int index, float[] embedding) {}
}
