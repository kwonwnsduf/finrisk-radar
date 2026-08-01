package com.finrisk.radar.collector.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3StorageConfiguration {
	@Bean(destroyMethod = "close")
	@Conditional(S3ConfiguredCondition.class)
	S3Client applicationS3Client(S3Properties properties) {
		return S3Client.builder().region(Region.of(properties.region())).build();
	}

	@Bean
	RawMarketDataStorage rawMarketDataStorage(
			S3Properties properties, ObjectMapper mapper, ObjectProvider<S3Client> clients) {
		S3Client client = clients.getIfAvailable();
		if (!properties.configured() || client == null) return new UnavailableRawMarketDataStorage();
		return new S3RawMarketDataStorage(client, mapper, properties.bucket());
	}

	static class S3ConfiguredCondition implements Condition {
		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			String region = context.getEnvironment().getProperty("app.market-data.s3.region");
			String bucket = context.getEnvironment().getProperty("app.market-data.s3.bucket");
			return hasText(region) && hasText(bucket);
		}

		private static boolean hasText(String value) {
			return value != null && !value.isBlank();
		}
	}
}
