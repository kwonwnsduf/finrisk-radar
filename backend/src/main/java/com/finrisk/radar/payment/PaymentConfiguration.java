package com.finrisk.radar.payment;

import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentConfiguration {
  @Bean
  PaymentGateway paymentGateway(
      PaymentProperties properties,
      @Value("${TOSS_SECRET_KEY:}") String secretKey,
      RestClient.Builder builder) {
    if (!properties.enabled()) return new DisabledPaymentGateway();
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalStateException("TOSS_SECRET_KEY is required when payment is enabled.");
    }
    if (!secretKey.contains("_gsk_")) {
      throw new IllegalStateException(
          "TOSS_SECRET_KEY must be a payment-widget secret key containing '_gsk_'.");
    }
    HttpClient client =
        HttpClient.newBuilder().connectTimeout(properties.provider().connectTimeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
    requestFactory.setReadTimeout(properties.provider().readTimeout());
    return new TossPaymentGateway(
        builder.baseUrl(properties.provider().baseUrl()).requestFactory(requestFactory).build(),
        secretKey);
  }
}
