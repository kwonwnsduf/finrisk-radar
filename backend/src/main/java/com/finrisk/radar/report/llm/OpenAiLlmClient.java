package com.finrisk.radar.report.llm;

import com.fasterxml.jackson.databind.*;
import io.micrometer.core.instrument.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
@EnableConfigurationProperties(LlmProperties.class)
public class OpenAiLlmClient implements LlmClient {
  private final LlmProperties properties;
  private final RestClient client;
  private final ObjectMapper mapper;
  private final MeterRegistry meters;

  @Autowired
  public OpenAiLlmClient(
      LlmProperties properties,
      RestClient.Builder builder,
      ObjectMapper mapper,
      MeterRegistry meters) {
    this(properties, createClient(properties, builder), mapper, meters);
  }

  OpenAiLlmClient(
      LlmProperties properties, RestClient client, ObjectMapper mapper, MeterRegistry meters) {
    this.properties = properties;
    this.client = client;
    this.mapper = mapper;
    this.meters = meters;
  }

  private static RestClient createClient(LlmProperties properties, RestClient.Builder builder) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    return builder
        .requestFactory(factory)
        .baseUrl(properties.baseUrl())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .build();
  }

  @Override
  public LlmResponse generate(LlmRequest request) {
    if (!configured())
      throw new LlmClientException(
          "LLM_NOT_CONFIGURED", "OpenAI API key and OPENAI_LLM_MODEL must be configured.", false);
    validate(request);
    Timer.Sample timer = Timer.start(meters);
    meters.counter("llm.calls", "model", properties.model()).increment();
    try {
      JsonNode response =
          client
              .post()
              .uri("/v1/responses")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
              .body(body(request))
              .retrieve()
              .body(JsonNode.class);
      LlmResponse parsed = parse(response);
      meters.counter("llm.success", "model", properties.model()).increment();
      meters
          .counter("llm.tokens.input", "model", properties.model())
          .increment(parsed.usage().inputTokens());
      meters
          .counter("llm.tokens.output", "model", properties.model())
          .increment(parsed.usage().outputTokens());
      return parsed;
    } catch (LlmClientException exception) {
      meters
          .counter("llm.failure", "model", properties.model(), "result", exception.getCode())
          .increment();
      throw exception;
    } catch (RestClientResponseException exception) {
      int status = exception.getStatusCode().value();
      boolean retryable = status == 429 || exception.getStatusCode().is5xxServerError();
      throw new LlmClientException(
          "OPENAI_HTTP_" + status,
          "OpenAI response request failed with HTTP " + status + ".",
          retryable,
          exception);
    } catch (ResourceAccessException exception) {
      throw new LlmClientException(
          "OPENAI_NETWORK_ERROR", "OpenAI response request failed.", true, exception);
    } catch (RestClientException exception) {
      throw new LlmClientException(
          "OPENAI_CLIENT_ERROR", "OpenAI response could not be processed.", false, exception);
    } finally {
      timer.stop(meters.timer("llm.duration", "model", properties.model()));
    }
  }

  private Map<String, Object> body(LlmRequest request) {
    Map<String, Object> format = new LinkedHashMap<>();
    format.put("type", "json_schema");
    format.put("name", request.schemaName());
    format.put("strict", true);
    format.put("schema", request.schema());
    return Map.of(
        "model", properties.model(),
        "store", false,
        "max_output_tokens", properties.maxOutputTokens(),
        "input",
            List.of(
                Map.of("role", "developer", "content", request.developerPrompt()),
                Map.of("role", "user", "content", request.userPrompt())),
        "text", Map.of("format", format));
  }

  private LlmResponse parse(JsonNode response) {
    if (response == null || response.isNull())
      throw new LlmClientException(
          "LLM_RESPONSE_EMPTY", "OpenAI returned an empty response.", false);
    LlmUsage usage =
        new LlmUsage(
            response.path("usage").path("input_tokens").asLong(0),
            response.path("usage").path("output_tokens").asLong(0));
    if ("incomplete".equals(response.path("status").asText()))
      throw new LlmClientException(
          "LLM_RESPONSE_INCOMPLETE", "OpenAI response was incomplete.", false, usage, null);
    for (JsonNode output : response.path("output")) {
      if (!"message".equals(output.path("type").asText())) continue;
      for (JsonNode content : output.path("content")) {
        if ("refusal".equals(content.path("type").asText()))
          throw new LlmClientException(
              "LLM_REFUSAL", "OpenAI refused the request.", false, usage, null);
        if ("output_text".equals(content.path("type").asText())) {
          String text = content.path("text").asText();
          try {
            mapper.readTree(text);
          } catch (Exception exception) {
            throw new LlmClientException(
                "LLM_JSON_INVALID", "OpenAI returned invalid JSON.", false, usage, exception);
          }
          return new LlmResponse(text, response.path("model").asText(properties.model()), usage);
        }
      }
    }
    throw new LlmClientException(
        "LLM_OUTPUT_MISSING",
        "OpenAI response did not contain structured output.",
        false,
        usage,
        null);
  }

  private void validate(LlmRequest request) {
    if (request == null
        || request.developerPrompt() == null
        || request.userPrompt() == null
        || request.schema() == null
        || request.schemaName() == null)
      throw new LlmClientException("LLM_REQUEST_INVALID", "LLM request is incomplete.", false);
    if (request.developerPrompt().length() + request.userPrompt().length()
        > properties.maxContextCharacters())
      throw new LlmClientException(
          "LLM_CONTEXT_TOO_LARGE", "LLM context exceeds the configured limit.", false);
  }

  @Override
  public boolean configured() {
    return properties.configured();
  }

  @Override
  public String modelName() {
    return properties.model();
  }
}
