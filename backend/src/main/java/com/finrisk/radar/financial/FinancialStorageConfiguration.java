package com.finrisk.radar.financial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.collector.storage.S3Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class FinancialStorageConfiguration {
	@Bean
	FinancialRawStorage financialRawStorage(S3Properties properties, ObjectMapper mapper) {
		if (!properties.configured()) return new UnavailableFinancialRawStorage();
		S3Client client = S3Client.builder().region(Region.of(properties.region()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
				.build();
		return new S3FinancialRawStorage(client, mapper, properties.bucket());
	}
}
